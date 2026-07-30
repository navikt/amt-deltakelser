package no.nav.amt.aktivitetskort.client

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "amt-arena-acl", types = [AmtArenaAclApi::class])
@ImportHttpServices(group = "aktivitet-arena-acl", types = [AktivitetArenaAclApi::class])
@ImportHttpServices(group = "amt-arrangor", types = [AmtArrangorApi::class])
@ImportHttpServices(group = "veilarboppfolging", types = [VeilarboppfolgingApi::class])
class ClientConfig

@Configuration(proxyBeanMethods = false)
class OAuth2ClientConfig {
    /**
     * Disables Spring Security's default web security (CSRF, login page).
     * Required because spring-boot-security-oauth2-client pulls in spring-security-web.
     * This service has no protected endpoints — it's a Kafka consumer with internal admin endpoints only.
     */
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
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
