package no.nav.tiltaksarrangor.client

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import java.time.Instant

@TestConfiguration
class OAuth2ClientTestConfig {
    private val mocks = mutableMapOf<String, MockRestServiceServer>()

    @Bean
    fun mockServerConfigurer(environment: Environment) = RestClientHttpServiceGroupConfigurer { groups ->
        groups.forEachClient { group, builder ->
            val baseUrl = environment.getRequiredProperty("spring.http.serviceclient.${group.name()}.base-url")
            builder.baseUrl(baseUrl)
            mocks[group.name()] = MockRestServiceServer.bindTo(builder).build()
        }
    }

    @Bean
    fun oauth2AuthorizedClientManager(): OAuth2AuthorizedClientManager = OAuth2AuthorizedClientManager { request ->
        val registrationId = request.clientRegistrationId

        val registration = ClientRegistration
            .withRegistrationId(registrationId)
            .clientId(registrationId)
            .authorizationGrantType(
                if (registrationId == AMT_ARRANGOR_TOKENX_CLIENT_ID) {
                    AuthorizationGrantType("urn:ietf:params:oauth:grant-type:token-exchange")
                } else {
                    AuthorizationGrantType.CLIENT_CREDENTIALS
                },
            ).tokenUri("http://localhost:9999/token")
            .build()

        OAuth2AuthorizedClient(
            registration,
            request.principal.name,
            OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "$registrationId-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
            ),
        )
    }

    @Bean
    fun oauth2Configurer(manager: OAuth2AuthorizedClientManager) = OAuth2RestClientHttpServiceGroupConfigurer.from(manager)

    fun getMock(group: String): MockRestServiceServer = mocks[group] ?: error("No mock for group '$group'")
}
