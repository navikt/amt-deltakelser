package no.nav.amt.aktivitetskort.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

@Service
class MetricsService(
    private val registry: MeterRegistry,
) {
    fun incMottattFeilmelding(errorType: String) {
        Counter
            .builder("amt_aktvkortpublisher_feilmelding")
            .tags("feiltype", errorType)
            .register(registry)
            .increment()
    }

    fun incSendtAktivitetskort() {
        Counter
            .builder("amt_aktvkortpublisher_sendt")
            .register(registry)
            .increment()
    }
}
