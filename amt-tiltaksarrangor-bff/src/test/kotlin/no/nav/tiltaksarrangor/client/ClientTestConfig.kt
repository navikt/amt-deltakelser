package no.nav.tiltaksarrangor.client

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.HttpServiceGroup
import java.time.Instant

@TestConfiguration(proxyBeanMethods = false)
class ClientTestConfig {
    private val mocks = mutableMapOf<String, MockRestServiceServer>()

    @Bean
    fun mockServerConfigurer(): RestClientHttpServiceGroupConfigurer = RestClientHttpServiceGroupConfigurer { groups ->
        groups.forEachClient { group: HttpServiceGroup, builder: RestClient.Builder ->
            mocks[group.name()] = MockRestServiceServer.bindTo(builder).build()
        }
    }

    @Bean
    fun authorizedClientManager(): OAuth2AuthorizedClientManager {
        val accessToken = OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            TOKEN,
            Instant.now(),
            Instant.now().plusSeconds(3600),
        )

        return OAuth2AuthorizedClientManager { request ->
            val registrationId = request.clientRegistrationId
            check(registrationId in REGISTRATION_IDS) {
                "Unknown client registration: '$registrationId'. Expected one of $REGISTRATION_IDS"
            }
            val registration = ClientRegistration
                .withRegistrationId(registrationId)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientId("test")
                .clientSecret("test-secret")
                .tokenUri("http://localhost:9999/token")
                .build()
            OAuth2AuthorizedClient(registration, "test-principal", accessToken)
        }
    }

    @Bean
    fun oauth2Configurer(manager: OAuth2AuthorizedClientManager): OAuth2RestClientHttpServiceGroupConfigurer =
        OAuth2RestClientHttpServiceGroupConfigurer.from(manager)

    fun getMock(group: String): MockRestServiceServer = mocks[group] ?: error("No mock for group '$group'")

    companion object {
        const val TOKEN = "test-token"

        private val REGISTRATION_IDS = setOf(
            "amt-arrangor-tokenx",
            "amt-arrangor-aad",
            "amt-person-aad",
        )
    }
}
