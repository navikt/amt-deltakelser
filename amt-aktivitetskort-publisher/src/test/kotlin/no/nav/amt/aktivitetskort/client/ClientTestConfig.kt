package no.nav.amt.aktivitetskort.client

import io.mockk.every
import io.mockk.mockk
import no.nav.amt.aktivitetskort.client.RestClientTestBase.Companion.TOKEN_IN_TEST
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.client.spring.oauth2.ClientConfigurationPropertiesMatcher
import no.nav.security.token.support.client.spring.oauth2.OAuth2ClientRequestInterceptor
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration(proxyBeanMethods = false)
class ClientTestConfig {
    private val clientProperties = mockk<ClientProperties>(relaxed = true)

    private val mockClientConfigurationProperties = mockk<ClientConfigurationProperties>().also {
        every { it.registration } returns registrationNames.associateWith { clientProperties }
    }

    private val mockOAuthAccessTokenService = mockk<OAuth2AccessTokenService>().also { service ->
        val tokenResponse = mockk<OAuth2AccessTokenResponse> {
            every { access_token } returns TOKEN_IN_TEST
        }
        every { service.getAccessToken(any()) } returns tokenResponse
    }

    @Bean
    fun oAuth2RestClientCustomizer() = RestClientCustomizer {
        it.requestInterceptor(
            OAuth2ClientRequestInterceptor(
                properties = mockClientConfigurationProperties,
                service = mockOAuthAccessTokenService,
                matcher = object : ClientConfigurationPropertiesMatcher {},
            ),
        )
    }

    companion object {
        private val registrationNames = listOf(
            "arena-acl",
            "arrangor",
            "aktivitet-arena-acl",
            "veilarboppfolging",
        )
    }
}
