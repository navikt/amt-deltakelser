package no.nav.amt.aktivitetskort.unleash

import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration(proxyBeanMethods = false)
class UnleashTestConfiguration {
    @Bean
    fun unleashClient() = FakeUnleash().apply { enableAll() }

    @Bean
    fun unleashToggle(unleash: Unleash) = CommonUnleashToggle(unleash)
}
