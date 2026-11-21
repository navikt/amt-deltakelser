package no.nav.amt.lib.outbox.metrics

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.junit.jupiter.api.Test

class PrometheusOutboxMeterTest {
    @Test
    fun `outbox-metrikker kan registreres med runtime-avhengighetene`() {
        shouldNotThrowAny {
            PrometheusOutboxMeter(PrometheusRegistry())
        }
    }
}
