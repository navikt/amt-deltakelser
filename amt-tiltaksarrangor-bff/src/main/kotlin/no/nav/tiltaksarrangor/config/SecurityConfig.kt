package no.nav.tiltaksarrangor.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.DelegatingOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer
import org.springframework.security.web.SecurityFilterChain

@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .csrf { it.disable() }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .logout { it.disable() }
        .oauth2ResourceServer { it.jwt {} }
        .authorizeHttpRequests {
            it
                .requestMatchers("/internal/**")
                .permitAll()
                .anyRequest()
                .authenticated()
        }.build()

    @Bean
    fun authorizedClientManager(clientRegistrationRepository: ClientRegistrationRepository): OAuth2AuthorizedClientManager =
        AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository,
            InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository),
        ).apply {
            setAuthorizedClientProvider(
                DelegatingOAuth2AuthorizedClientProvider(
                    OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build(),
                    TokenExchangeOAuth2AuthorizedClientProvider(),
                ),
            )
        }

    @Bean
    fun oauth2Configurer(manager: OAuth2AuthorizedClientManager): OAuth2RestClientHttpServiceGroupConfigurer =
        OAuth2RestClientHttpServiceGroupConfigurer.from(manager)
}
