package no.nav.tiltaksarrangor.config

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("default")
@Configuration(proxyBeanMethods = false)
class UnleashConfig {
	@Bean
	fun unleashClient(
		@Value($$"${app.env.unleashUrl}") unleashUrl: String,
		@Value($$"${app.env.unleashApiToken}") unleashApiToken: String,
	) = DefaultUnleash(
		UnleashConfig
			.builder()
			.appName(APP_NAME)
			.instanceId(APP_NAME)
			.unleashAPI(unleashUrl)
			.apiKey(unleashApiToken)
			.build(),
	)

	@Bean
	fun commonUnleashToggle(unleash: Unleash): CommonUnleashToggle = CommonUnleashToggle(unleash)

	companion object {
		const val APP_NAME = "amt-tiltaksarrangor-bff"
	}
}
