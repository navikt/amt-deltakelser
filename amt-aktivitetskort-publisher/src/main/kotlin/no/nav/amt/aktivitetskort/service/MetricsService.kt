package no.nav.amt.aktivitetskort.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class MetricsService(
    private val registry: MeterRegistry,
    jdbcTemplate: JdbcTemplate,
) {
    init {
        Gauge
            .builder(
                "amt_aktivitetskortpublisher_kafka_outbox_ventende",
                jdbcTemplate,
            ) {
                it
                    .queryForObject(
                        "SELECT count(*) FROM kafka_producer_record",
                        Long::class.java,
                    )!!
                    .toDouble()
            }.register(registry)
    }

    fun incMottattFeilmelding(errorType: String) {
        Counter
            .builder("amt_aktvkortpublisher_feilmelding")
            .tags("feiltype", errorType)
            .register(registry)
            .increment()
    }
}
