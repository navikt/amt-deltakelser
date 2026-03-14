package no.nav.tiltaksarrangor.config

import no.nav.common.audit_log.log.AuditLogger
import no.nav.common.audit_log.log.AuditLoggerImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class LogConfig {
	@Bean
	fun auditLogger(): AuditLogger = AuditLoggerImpl()
}
