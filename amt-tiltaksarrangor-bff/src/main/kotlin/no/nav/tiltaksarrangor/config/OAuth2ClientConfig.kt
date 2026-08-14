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

@Configuration(proxyBeanMethods = false)
class OAuth2ClientConfig {
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

    @Bean
    fun oauth2Configurer(manager: OAuth2AuthorizedClientManager) = OAuth2RestClientHttpServiceGroupConfigurer.from(manager)

    private fun tokenExchangeResponseClient() = RestClientTokenExchangeTokenResponseClient().apply {
        addParametersConverter { grantRequest ->
            LinkedMultiValueMap<String, String>().apply {
                add(
                    "audience",
                    grantRequest.clientRegistration.scopes.single(),
                )
            }
        }

        val jwtConverter = NimbusJwtClientAuthenticationParametersConverter<TokenExchangeGrantRequest> { registration ->
            JWK.parse(registration.clientSecret)
        }

        addParametersConverter { grantRequest ->
            jwtConverter.convert(grantRequest)!!
        }
    }
}
