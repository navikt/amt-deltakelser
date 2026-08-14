package no.nav.tiltaksarrangor.config

import no.nav.tiltaksarrangor.api.InternalAuthorizationManager
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusScrapeEndpoint
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.OrRequestMatcher

@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        internalAuthorizationManager: InternalAuthorizationManager,
    ): SecurityFilterChain {
        http {
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            csrf { disable() }
            logout { disable() }
            oauth2ResourceServer { jwt { } }
            authorizeHttpRequests {
                authorize(
                    OrRequestMatcher(
                        EndpointRequest.to(HealthEndpoint::class.java),
                        EndpointRequest.to(PrometheusScrapeEndpoint::class.java),
                    ),
                    permitAll,
                )
                authorize("/internal/**", internalAuthorizationManager)
                authorize(anyRequest, authenticated)
            }
        }

        return http.build()
    }
}
