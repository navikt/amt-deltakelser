package no.nav.amt.lib.outbox

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * Represents an record that is stored in the outbox, waiting to be published.
 *
 * To store records create a postgres table with the following SQL:
 * ```sql
 * CREATE TABLE outbox_record (
 * id SERIAL PRIMARY KEY,
 * key VARCHAR(255) NOT NULL,
 * value JSONB NOT NULL,
 * value_type VARCHAR(255) NOT NULL,
 * topic VARCHAR(255) NOT NULL,
 * created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 * modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 * processed_at TIMESTAMPTZ,
 * status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
 * retry_count INT NOT NULL DEFAULT 0,
 * retried_at TIMESTAMPTZ,
 * error_message TEXT
 * );
 *
 * CREATE INDEX idx_outbox_status_created ON outbox_records(status, created_at);
 * ```
 *
 * @property id The unique identifier of the record.
 * @property key The key of the record, used for partitioning in Kafka.
 * @property value The payload of the record, as a JSON object.
 * @property valueType The type of the payload, used for deserialization.
 * @property topic The Kafka topic to which the record will be published.
 * @property createdAt The timestamp when the record was created.
 * @property processedAt The timestamp when the record was successfully processed. Null if not yet processed.
 * @property status The current status of the record (e.g., PENDING, PROCESSED, FAILED).
 * @property retryCount The number of times the processing of this record has been attempted.
 * @property retriedAt The last time the record was retried.
 * @property errorMessage An optional error message if the record processing failed.
 */
data class OutboxRecord(
    val id: OutboxRecordId,
    val key: String,
    val value: JsonNode,
    val valueType: String,
    val topic: String,
    val createdAt: LocalDateTime,
    val processedAt: LocalDateTime? = null,
    val status: OutboxRecordStatus,
    val retryCount: Int = 0,
    val retriedAt: LocalDateTime? = null,
    val errorMessage: String? = null,
)

/**
 * A data class for creating a new outbox record.
 * It contains the essential information required to construct an [OutboxRecord].
 *
 * @property key The key of the record.
 * @property value The payload of the record.
 * @property valueType The type of the payload.
 * @property topic The target Kafka topic.
 */
internal data class NewOutboxRecord(
    val key: String,
    val value: JsonNode,
    val valueType: String,
    val topic: String,
)

@JvmInline
value class OutboxRecordId(
    val value: Long,
) {
    override fun toString(): String = value.toString()
}

enum class OutboxRecordStatus {
    PENDING,
    PROCESSED,
    FAILED,
}
