package no.nav.amt.lib.outbox.metrics

import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.amt.lib.outbox.OutboxRecordStatus

class PrometheusOutboxMeter(
    registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
) : OutboxMeter {
    private val newRecordsCounter = Counter
        .builder()
        .name("amt_kafka_outbox_new_records_total")
        .help("Total number of new outbox records")
        .labelNames("topic")
        .register(registry)

    private val processedRecordsCounter = Counter
        .builder()
        .name("amt_kafka_outbox_processed_records_total")
        .help("Total number of processed outbox records")
        .labelNames("topic", "status")
        .register(registry)

    override fun incrementNewRecords(topic: String) {
        newRecordsCounter.labelValues(topic).inc()
    }

    override fun incrementProcessedRecords(topic: String, status: OutboxRecordStatus) {
        processedRecordsCounter.labelValues(topic, status.name).inc()
    }
}
