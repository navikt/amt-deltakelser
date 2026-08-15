package no.nav.tiltaksarrangor.config

import com.nimbusds.jose.jwk.JWK
import no.nav.tiltaksarrangor.client.TexasTokenExchangeClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.DelegatingOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse
import org.springframework.util.LinkedMultiValueMap
import java.time.Instant

/**
 * Konfigurerer OAuth2-klientstøtte for utgående HTTP-kall.
 *
 * Oppsettet støtter både:
 * - `client_credentials` for maskin-til-maskin-kall uten brukerkontekst
 * - TokenX (`token_exchange`) for videresending av brukerkontekst mot downstream-tjenester
 *
 * TokenX token exchange kan kjøres enten via Texas eller legacy token-endepunkt.
 */
@Configuration(proxyBeanMethods = false)
class OAuth2ClientConfig(
    private val texasTokenExchangeClient: TexasTokenExchangeClient,
    @Value($$"${app.auth.tokenx.use-texas-token-exchange}")
    private val useTexasTokenExchange: Boolean,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

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

    private fun tokenExchangeResponseClient(): OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest> = if (useTexasTokenExchange) {
        log.debug("TokenX token exchange mode=texas")
        texasTokenExchangeResponseClient()
    } else {
        log.debug("TokenX token exchange mode=legacy")
        legacyTokenExchangeResponseClient()
    }

    private fun texasTokenExchangeResponseClient(): OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest> =
        OAuth2AccessTokenResponseClient { grantRequest ->
            val audience = grantRequest.getAudienceOrThrow()
            log.debug(
                "TokenX token exchange via texas: registrationId={}, audience={}",
                grantRequest.clientRegistration.registrationId,
                audience,
            )
            val tokenResponse = texasTokenExchangeClient.exchangeToken(
                userToken = grantRequest.subjectToken.tokenValue,
                target = audience,
            )
            log.debug(
                "TokenX token exchange via texas succeeded: registrationId={}, expiresIn={}",
                grantRequest.clientRegistration.registrationId,
                tokenResponse.expiresIn,
            )

            OAuth2AccessTokenResponse
                .withToken(tokenResponse.accessToken)
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(tokenResponse.expiresIn)
                .build()
        }

    private fun legacyTokenExchangeResponseClient(): OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest> =
        RestClientTokenExchangeTokenResponseClient().apply {
            val jwtConverter = NimbusJwtClientAuthenticationParametersConverter<TokenExchangeGrantRequest> { registration ->
                JWK.parse(registration.clientSecret)
            }.apply {
                setJwtClientAssertionCustomizer {
                    it.claims.notBefore(Instant.now().minusSeconds(5))
                }
            }

            addParametersConverter { grantRequest ->
                val audience = grantRequest.getAudienceOrThrow()
                log.debug(
                    "TokenX token exchange via legacy: registrationId={}, audience={}",
                    grantRequest.clientRegistration.registrationId,
                    audience,
                )
                val jwtParameters = jwtConverter.convert(grantRequest)
                    ?: throw IllegalArgumentException(
                        "Could not create client assertion for '${grantRequest.clientRegistration.registrationId}'",
                    )

                LinkedMultiValueMap<String, String>()
                    .apply {
                        add("audience", audience)
                        addAll(jwtParameters)
                    }
            }
        }

    private fun TokenExchangeGrantRequest.getAudienceOrThrow(): String = this.clientRegistration.scopes.singleOrNull()
        ?: throw IllegalArgumentException(
            "Expected exactly one scope for token exchange audience in client registration '${this.clientRegistration.registrationId}'",
        )
}
