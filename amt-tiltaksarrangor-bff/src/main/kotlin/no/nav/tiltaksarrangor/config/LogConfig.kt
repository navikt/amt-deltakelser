package no.nav.tiltaksarrangor.config

import no.nav.common.audit_log.log.AuditLogger
import no.nav.common.audit_log.log.AuditLoggerImpl
import no.nav.common.rest.filter.LogRequestFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class LogConfig {
    @Bean
    fun auditLogger(): AuditLogger = AuditLoggerImpl()

    @Bean
    fun logFilterRegistrationBean() = FilterRegistrationBean<LogRequestFilter>().apply {
        setFilter(LogRequestFilter("amt-tiltaksarrangor-bff", false))
        order = 1
        addUrlPatterns("/*")
    }
}
