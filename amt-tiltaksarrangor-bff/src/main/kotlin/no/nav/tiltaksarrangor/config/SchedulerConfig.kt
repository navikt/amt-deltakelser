package no.nav.tiltaksarrangor.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "30m", defaultLockAtLeastFor = "10m")
@Configuration(proxyBeanMethods = false)
class SchedulerConfig {
	@Bean
	fun lockProvider(jdbcTemplate: JdbcTemplate): LockProvider = JdbcTemplateLockProvider(
		JdbcTemplateLockProvider.Configuration
			.builder()
			.withJdbcTemplate(jdbcTemplate)
			.usingDbTime()
			.build(),
	)
}
