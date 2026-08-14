package no.nav.tiltaksarrangor.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer

@Configuration(proxyBeanMethods = false)
class OAuth2ClientConfig {
    // denne er påkrevet for jobber og Kafka-klienter hvor det ikke er en aktiv request
    @Bean
    fun azureAdAuthorizedClientManager(
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
    fun tokenXAuthorizedClientManager(
        clientRegistrationRepository: ClientRegistrationRepository,
        authorizedClientRepository: OAuth2AuthorizedClientRepository,
    ): OAuth2AuthorizedClientManager = DefaultOAuth2AuthorizedClientManager(
        clientRegistrationRepository,
        authorizedClientRepository,
    ).apply {
        setAuthorizedClientProvider(
            TokenExchangeOAuth2AuthorizedClientProvider(),
        )
    }

    @Bean
    fun oauth2Configurer(
        @Qualifier("azureAdAuthorizedClientManager") manager: OAuth2AuthorizedClientManager,
    ) = OAuth2RestClientHttpServiceGroupConfigurer.from(manager)
}
