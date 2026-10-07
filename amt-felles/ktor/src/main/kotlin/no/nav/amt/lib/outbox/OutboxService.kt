package no.nav.amt.lib.outbox

import no.nav.amt.lib.outbox.metrics.OutboxMeter
import no.nav.amt.lib.outbox.metrics.PrometheusOutboxMeter
import no.nav.amt.lib.utils.database.jdbi.DatabaseApi
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.toPGObject
import org.slf4j.LoggerFactory

/**
 * Provides a high-level API for interacting with the outbox.
 * This service simplifies the creation and management of outbox events,
 * abstracting away the underlying repository details.
 */
class OutboxService(
    private val meter: OutboxMeter = PrometheusOutboxMeter(),
) : OutboxInserter {
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
    override fun <K : Any, V : Any> insertRecord(
        key: K,
        value: V,
        topic: String,
        suppressOutsideTxWarning: Boolean,
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
     * Creates an outbox event that will be published as a Kafka tombstone.
     *
     * @param key The key of the event.
     * @param topic The Kafka topic to which the tombstone will be published.
     * @param suppressOutsideTxWarning Whether to suppress the warning when called outside a transaction.
     * @return The created [OutboxRecord].
     */
    fun <K : Any> insertTombstone(
        key: K,
        topic: String,
        suppressOutsideTxWarning: Boolean = false,
    ): OutboxRecord {
        val outboxRecord = NewOutboxRecord(
            key = key.toString(),
            valueType = OUTBOX_TOMBSTONE_VALUE_TYPE,
            topic = topic,
            value = objectMapper.readTree("null"),
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
        outboxRepository.deleteOutboxRecord(record.id)
        meter.incrementProcessedRecords(record.topic, OutboxRecordStatus.PROCESSED)
    }

    /**
     * Marks an outbox record as failed.
     *
     * @param record The failed record.
     * @param errorMessage A message describing the reason for the failure.
     */
    fun markAsFailed(
        record: OutboxRecord,
        errorMessage: String,
    ) {
        outboxRepository.markAsFailed(record.id, errorMessage)
        meter.incrementProcessedRecords(record.topic, OutboxRecordStatus.FAILED)
    }

    fun jdbiInserter(db: DatabaseApi): OutboxInserter = OutboxJdbiInsertService(db, meter)
}

interface OutboxInserter {
    fun <K : Any, V : Any> insertRecord(
        key: K,
        value: V,
        topic: String,
        suppressOutsideTxWarning: Boolean = false,
    ): OutboxRecord
}

/**
 * Applikasjoner som har tatt i bruk Jdbi som database-API må bruke denne for at outbox-logikken skal kunne kjøre i transaksjon.
 */
class OutboxJdbiInsertService(
    private val db: DatabaseApi,
    private val meter: OutboxMeter = PrometheusOutboxMeter(),
) : OutboxInserter {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun <K : Any, V : Any> insertRecord(
        key: K,
        value: V,
        topic: String,
        suppressOutsideTxWarning: Boolean,
    ): OutboxRecord {
        val outboxRecord = NewOutboxRecord(
            key = key.toString(),
            valueType = value::class.java.simpleName,
            topic = topic,
            value = objectMapper.valueToTree(value),
        )

        return db.forbindelse {
            val inTransaction = it.erTransaksjon()

            if (!(suppressOutsideTxWarning || inTransaction)) {
                logger.warn(
                    "OutboxRepository.insertNewRecord called outside of transaction. Topic: {}, key: {}",
                    topic,
                    key,
                )
            }
            it
                .bruk(OutboxRepositoryJdbi::class)
                .insertNewRecord(
                    key = outboxRecord.key,
                    value = objectMapper.toPGObject(outboxRecord.value),
                    valueType = outboxRecord.valueType,
                    topic = outboxRecord.topic,
                ).also { meter.incrementNewRecords(topic) }
        }
    }
}
