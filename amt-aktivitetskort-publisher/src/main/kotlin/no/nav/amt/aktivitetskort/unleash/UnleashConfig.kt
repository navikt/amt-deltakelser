package no.nav.amt.aktivitetskort.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
class UnleashConfig {
    @Bean
    @Profile("default")
    fun unleashClient(
        @Value($$"${UNLEASH_SERVER_API_URL}") unleashUrl: String,
        @Value($$"${UNLEASH_SERVER_API_TOKEN}") unleashApiToken: String,
    ): Unleash {
        val config = UnleashConfig
            .builder()
            .appName(AKTIVITETSKORT_APP_NAME)
            .instanceId(AKTIVITETSKORT_APP_NAME)
            .unleashAPI("$unleashUrl/api")
            .apiKey(unleashApiToken)
            .build()

        return DefaultUnleash(config)
    }

    @Bean
    fun commonUnleashToggle(unleash: Unleash): CommonUnleashToggle = CommonUnleashToggle(unleash)

    companion object {
        const val AKTIVITETSKORT_APP_NAME = "amt-aktivitetskort-publisher"
    }
}
