package no.nav.tiltaksarrangor.client

import io.mockk.every
import io.mockk.mockk
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration(proxyBeanMethods = false)
class ClientTestConfig {
    @Bean
    fun mockOAuth2AccessTokenService(): OAuth2AccessTokenService {
        val service = mockk<OAuth2AccessTokenService>(relaxed = true)
        val tokenResponse = mockk<OAuth2AccessTokenResponse> {
            every { access_token } returns "test-token"
        }
        every { service.getAccessToken(any()) } returns tokenResponse
        return service
    }

    @Bean
    fun mockClientConfigurationProperties(): ClientConfigurationProperties {
        val properties = mockk<ClientConfigurationProperties>(relaxed = true)
        val clientProperties = mockk<ClientProperties>(relaxed = true)
        val registration = mapOf(
            "amt-person-aad" to clientProperties,
            "amt-arrangor-tokenx" to clientProperties,
            "amt-arrangor-aad" to clientProperties,
        )
        every { properties.registration } returns registration
        return properties
    }
}
