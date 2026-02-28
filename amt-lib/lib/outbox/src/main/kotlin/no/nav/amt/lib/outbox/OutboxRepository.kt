package no.nav.amt.lib.outbox

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import org.postgresql.util.PGobject
import org.slf4j.LoggerFactory

internal class OutboxRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    internal fun insertNewRecord(
        record: NewOutboxRecord,
        suppressOutsideTxWarning: Boolean = true,
    ): OutboxRecord {
        val sql =
            """
            INSERT INTO outbox_record (
                key, 
                value, 
                value_type, 
                topic, 
                status, 
                retry_count
            )
            VALUES (
                :key, 
                :value, 
                :value_type, 
                :topic, 
                :status, 
                :retry_count 
            )
            RETURNING *
            """.trimIndent()

        val params =
            mapOf(
                "key" to record.key,
                "value" to toPGObject(record.value),
                "value_type" to record.valueType,
                "topic" to record.topic,
                "status" to OutboxRecordStatus.PENDING.name,
                "retry_count" to 0,
            )

        return Database.query { session ->
            if (!(suppressOutsideTxWarning || session is TransactionalSession)) {
                log.warn("OutboxRepository.insertNewRecord called outside of transaction. Topic: ${record.topic}, key: ${record.key}")
            }

            session.single(
                queryOf(sql, params),
                ::rowMapper,
            ) ?: throw NoSuchElementException(
                "Failed to insert OutboxRecord for key: ${record.key}, " +
                    "valueType: ${record.valueType} and topic: ${record.topic}",
            )
        }
    }

    fun findUnprocessedRecords(limit: Int): List<OutboxRecord> {
        val sql =
            """
            SELECT * 
            FROM outbox_record
            WHERE status IN ('${OutboxRecordStatus.PENDING.name}', '${OutboxRecordStatus.FAILED.name}')
            ORDER BY created_at
            LIMIT :limit
            """.trimIndent()

        return Database.query { session ->
            session.list(
                queryOf(sql, mapOf("limit" to limit)),
                ::rowMapper,
            )
        }
    }

    fun deletedOutboxRecord(recordId: OutboxRecordId) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM outbox_record WHERE id = :id",
                    mapOf("id" to recordId.value),
                ),
            )
        }
    }

    fun markAsFailed(
        recordId: OutboxRecordId,
        errorMessage: String,
    ) {
        val sql =
            """
            UPDATE outbox_record
            SET 
                status = '${OutboxRecordStatus.FAILED.name}', 
                error_message = :error_message, 
                retry_count = retry_count + 1,
                modified_at = CURRENT_TIMESTAMP,
                retried_at = CURRENT_TIMESTAMP
            WHERE id = :id
            """.trimIndent()

        val params =
            mapOf(
                "id" to recordId.value,
                "error_message" to errorMessage,
            )

        Database.query { session ->
            session.update(queryOf(sql, params))
        }
    }

    fun get(id: OutboxRecordId): OutboxRecord? =
        Database.query { session ->
            session.single(
                queryOf(
                    "SELECT * FROM outbox_record WHERE id = :id",
                    mapOf("id" to id.value),
                ),
                ::rowMapper,
            )
        }

    fun getRecordsByTopicAndKey(
        topic: String,
        key: String,
    ) = Database.query { session ->
        session.list(
            queryOf(
                "SELECT * FROM outbox_record WHERE key = :key AND topic = :topic",
                mapOf("key" to key, "topic" to topic),
            ),
            ::rowMapper,
        )
    }

    companion object {
        private fun toPGObject(value: Any?) =
            PGobject().also {
                it.type = "json"
                it.value = value?.let { v -> objectMapper.writeValueAsString(v) }
            }

        private fun rowMapper(row: Row) =
            OutboxRecord(
                id = OutboxRecordId(row.long("id")),
                key = row.string("key"),
                value = objectMapper.readTree(row.string("value")),
                valueType = row.string("value_type"),
                topic = row.string("topic"),
                createdAt = row.localDateTime("created_at"),
                processedAt = row.localDateTimeOrNull("processed_at"),
                status = OutboxRecordStatus.valueOf(row.string("status")),
                retryCount = row.int("retry_count"),
                retriedAt = row.localDateTimeOrNull("retried_at"),
                errorMessage = row.stringOrNull("error_message"),
            )
    }
}
