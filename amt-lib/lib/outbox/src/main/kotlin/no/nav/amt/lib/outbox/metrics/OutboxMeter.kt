package no.nav.amt.lib.outbox.metrics

import no.nav.amt.lib.outbox.OutboxRecordStatus

interface OutboxMeter {
    fun incrementNewRecords(topic: String)

    fun incrementProcessedRecords(topic: String, status: OutboxRecordStatus)
}

internal class NoOpOutboxMeter : OutboxMeter {
    override fun incrementNewRecords(topic: String) {}

    override fun incrementProcessedRecords(topic: String, status: OutboxRecordStatus) {}
}
