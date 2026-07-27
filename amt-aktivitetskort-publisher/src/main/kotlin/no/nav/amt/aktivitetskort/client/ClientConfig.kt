package no.nav.amt.aktivitetskort.client

import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.client.spring.oauth2.ClientConfigurationPropertiesMatcher
import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.client.spring.oauth2.OAuth2ClientRequestInterceptor
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableOAuth2Client(cacheEnabled = true)
class ClientConfig {
    @Bean
    fun customizer(requestInterceptor: OAuth2ClientRequestInterceptor) = RestClientCustomizer {
        it.requestInterceptor(requestInterceptor)
    }

    @Bean
    fun requestInterceptor(
        properties: ClientConfigurationProperties,
        service: OAuth2AccessTokenService,
        matcher: ClientConfigurationPropertiesMatcher,
    ) = OAuth2ClientRequestInterceptor(properties, service, matcher)

    @Bean
    fun configMatcher() = object : ClientConfigurationPropertiesMatcher {}
}
