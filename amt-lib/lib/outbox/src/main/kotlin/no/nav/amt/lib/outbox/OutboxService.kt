package no.nav.amt.lib.outbox

import no.nav.amt.lib.outbox.metrics.OutboxMeter
import no.nav.amt.lib.outbox.metrics.PrometheusOutboxMeter
import no.nav.amt.lib.utils.objectMapper

/**
 * Provides a high-level API for interacting with the outbox.
 * This service simplifies the creation and management of outbox events,
 * abstracting away the underlying repository details.
 */
class OutboxService(
    private val meter: OutboxMeter = PrometheusOutboxMeter(),
) {
    private val outboxRepository = OutboxRepository()

    /**
     * Creates a new outbox event and persists it to the database.
     *
     * @param K The type of the key being sent.
     * @param V The type of the value being sent.
     * @param key The key of the event, typically a unique identifier for the entity.
     * @param value The value (payload) of the event.
     * @param topic The Kafka topic to which the event will be published.
     * @return The created [OutboxRecord].
     */
    fun <K : Any, V : Any> insertRecord(
        key: K,
        value: V,
        topic: String,
        suppressOutsideTxWarning: Boolean = false,
    ): OutboxRecord {
        val outboxRecord = NewOutboxRecord(
            key = key.toString(),
            valueType = value::class.java.simpleName,
            topic = topic,
            value = objectMapper.valueToTree(value),
        )
        return outboxRepository
            .insertNewRecord(outboxRecord, suppressOutsideTxWarning)
            .also { meter.incrementNewRecords(topic) }
    }

    /**
     * Finds unprocessed outbox events (with status PENDING or FAILED).
     *
     * @param limit The maximum number of events to return.
     * @return A list of unprocessed [OutboxRecord]s.
     */
    fun findUnprocessedRecords(limit: Int): List<OutboxRecord> = outboxRepository.findUnprocessedRecords(limit)

    /**
     * Marks an outbox record as processed.
     *
     * @param record The record to mark as processed.
     */
    fun markAsProcessed(record: OutboxRecord) {
        outboxRepository.deletedOutboxRecord(record.id)
        meter.incrementProcessedRecords(record.topic, OutboxRecordStatus.PROCESSED)
    }

    /**
     * Marks an outbox record as failed.
     *
     * @param record The failed record.
     * @param errorMessage A message describing the reason for the failure.
     */
    fun markAsFailed(record: OutboxRecord, errorMessage: String) {
        outboxRepository.markAsFailed(record.id, errorMessage)
        meter.incrementProcessedRecords(record.topic, OutboxRecordStatus.FAILED)
    }

    fun getRecordsByTopicAndKey(topic: String, key: String): List<OutboxRecord> = outboxRepository.getRecordsByTopicAndKey(topic, key)
}
