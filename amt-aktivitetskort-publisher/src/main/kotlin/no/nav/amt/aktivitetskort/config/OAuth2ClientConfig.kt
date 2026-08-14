package no.nav.amt.aktivitetskort.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer

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
            OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build(),
        )
    }

    @Bean
    fun oauth2Configurer(manager: OAuth2AuthorizedClientManager) = OAuth2RestClientHttpServiceGroupConfigurer.from(manager)
}
