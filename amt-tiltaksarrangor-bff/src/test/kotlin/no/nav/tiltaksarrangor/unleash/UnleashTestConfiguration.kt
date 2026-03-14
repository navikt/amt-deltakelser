package no.nav.tiltaksarrangor.unleash

import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class UnleashTestConfiguration {
	@Bean
	fun unleashClient(): Unleash = FakeUnleash().apply {
		enableAll()
	}

	@Bean
	fun commonUnleashToggle(fakeUnleash: Unleash): CommonUnleashToggle = CommonUnleashToggle(fakeUnleash)
}
