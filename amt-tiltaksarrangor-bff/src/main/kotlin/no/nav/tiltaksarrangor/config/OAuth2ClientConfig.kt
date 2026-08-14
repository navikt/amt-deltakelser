package no.nav.tiltaksarrangor.config

import com.nimbusds.jose.jwk.JWK
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.DelegatingOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer
import org.springframework.util.LinkedMultiValueMap
import java.time.Instant

/**
 * Konfigurerer OAuth2-klientstøtte for utgående HTTP-kall.
 *
 * Oppsettet støtter både:
 * - `client_credentials` for maskin-til-maskin-kall uten brukerkontekst
 * - TokenX (`token_exchange`) for videresending av brukerkontekst mot downstream-tjenester
 *
 * For TokenX token exchange forventes det at hver `ClientRegistration` har nøyaktig ett scope, og at
 * dette scopet representerer audience som skal sendes til token-endepunktet.
 *
 * `clientSecret` tolkes som JWK for klientassertion (private_key_jwt).
 */
@Configuration(proxyBeanMethods = false)
class OAuth2ClientConfig {
    /**
     * Oppretter en [OAuth2AuthorizedClientManager] med støtte for både `client_credentials` og
     * `token_exchange`.
     *
     * @param clientRegistrationRepository repository med registrerte OAuth2-klienter.
     * @param authorizedClientService service for lagring/henting av autoriserte klienter.
     * @return konfigurert [OAuth2AuthorizedClientManager].
     */
    @Bean
    fun oauth2AuthorizedClientManager(
        clientRegistrationRepository: ClientRegistrationRepository,
        authorizedClientService: OAuth2AuthorizedClientService,
    ): OAuth2AuthorizedClientManager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
        clientRegistrationRepository,
        authorizedClientService,
    ).apply {
        setAuthorizedClientProvider(
            DelegatingOAuth2AuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder
                    .builder()
                    .clientCredentials()
                    .build(),
                TokenExchangeOAuth2AuthorizedClientProvider().apply {
                    setAccessTokenResponseClient(tokenExchangeResponseClient())
                },
            ),
        )
    }

    /**
     * Eksponerer Spring sin configurerer som knytter [OAuth2AuthorizedClientManager] til
     * HTTP service group-klienter.
     *
     * @param manager OAuth2-manager som håndterer token-innhenting og fornyelse.
     * @return [OAuth2RestClientHttpServiceGroupConfigurer] koblet til gitt manager.
     */
    @Bean
    fun oauth2Configurer(manager: OAuth2AuthorizedClientManager) = OAuth2RestClientHttpServiceGroupConfigurer.from(manager)

    /**
     * Lager token response-klient for TokenX token exchange.
     *
     * Legger til:
     * - `audience`-parameter basert på scope i client registration
     * - signert klientassertion (private_key_jwt) med eksplisitt `nbf`-claim
     *
     * @return [RestClientTokenExchangeTokenResponseClient] konfigurert for token exchange.
     */
    private fun tokenExchangeResponseClient() = RestClientTokenExchangeTokenResponseClient().apply {
        val jwtConverter = NimbusJwtClientAuthenticationParametersConverter<TokenExchangeGrantRequest> { registration ->
            JWK.parse(registration.clientSecret)
        }.apply {
            setJwtClientAssertionCustomizer {
                // Token-endepunktet krever `nbf`; vi setter den litt tilbake i tid for å tåle små klokkeskjevheter.
                it.claims.notBefore(Instant.now().minusSeconds(5))
            }
        }

        addParametersConverter { grantRequest ->
            val audience = grantRequest.clientRegistration.scopes.singleOrNull()
                ?: throw IllegalArgumentException(
                    "Expected exactly one scope for token exchange audience in client registration '${grantRequest.clientRegistration.registrationId}'",
                )

            val jwtParameters = jwtConverter.convert(grantRequest)
                ?: throw IllegalArgumentException(
                    "Could not create client assertion for '${grantRequest.clientRegistration.registrationId}'",
                )

            LinkedMultiValueMap<String, String>().apply {
                add("audience", audience)
                addAll(jwtParameters)
            }
        }
    }
}
