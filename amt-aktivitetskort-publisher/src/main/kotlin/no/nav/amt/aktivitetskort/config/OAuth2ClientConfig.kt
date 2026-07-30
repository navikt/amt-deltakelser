package no.nav.amt.aktivitetskort.config

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer

@Configuration(proxyBeanMethods = false)
class OAuth2ClientConfig {
    /**
     * Overrides Spring Security's default web security.
     * Required because spring-boot-security-oauth2-client pulls in spring-security-web.
     * This service has no browser-facing endpoints — it's a Kafka consumer with internal admin endpoints only.
     * CSRF is ignored for internal/actuator paths (machine-to-machine, behind Nais network policy).
     */
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .sessionManagement { session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .csrf { it.disable() }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .logout { it.disable() }
        .authorizeHttpRequests {
            it
                .requestMatchers(EndpointRequest.to(HealthEndpoint::class.java))
                .permitAll()
                .requestMatchers("/internal/**", "/actuator/**")
                .permitAll()
                .anyRequest()
                .authenticated()
        }.build()

    @Bean
    fun authorizedClientManager(repo: ClientRegistrationRepository): OAuth2AuthorizedClientManager =
        AuthorizedClientServiceOAuth2AuthorizedClientManager(
            repo,
            InMemoryOAuth2AuthorizedClientService(repo),
        ).apply {
            setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build(),
            )
        }

    @Bean
    fun oauth2Configurer(manager: OAuth2AuthorizedClientManager): OAuth2RestClientHttpServiceGroupConfigurer =
        OAuth2RestClientHttpServiceGroupConfigurer.from(manager)

    @Bean
    fun defaultHeadersConfigurer(): RestClientHttpServiceGroupConfigurer = RestClientHttpServiceGroupConfigurer { groups ->
        groups.forEachClient { _, builder ->
            builder.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        }
    }
}
