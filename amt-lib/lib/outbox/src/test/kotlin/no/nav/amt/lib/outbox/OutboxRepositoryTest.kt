package no.nav.amt.lib.outbox

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.lib.testing.TestPostgresContainer
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

class OutboxRepositoryTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupAll() = TestPostgresContainer.bootstrap()
    }

    @Test
    fun `test outbox repository`() {
        val repo = OutboxRepository()
        val record = NewOutboxRecord(
            key = "test-key",
            valueType = "test-value-type",
            topic = "test-topic",
            value = objectMapper.createObjectNode().put("key", "value"),
        )

        val recordWithId = repo.insertNewRecord(record)

        recordWithId.id shouldNotBe null
    }

    @Test
    fun `findUnprocessedRecords returns pending and failed records`() {
        val repo = OutboxRepository()

        val pendingRecord = NewOutboxRecord(
            key = "key-1",
            valueType = "type-1",
            topic = "topic-1",
            value = objectMapper.createObjectNode().put("key", "pending"),
        )
        repo.insertNewRecord(pendingRecord)

        val failedRecord = pendingRecord.copy(
            key = "key-2",
            value = objectMapper.createObjectNode().put("key", "failed"),
        )
        repo.insertNewRecord(failedRecord).also {
            repo.markAsFailed(it.id, "Some error")
        }

        val processedRecord = pendingRecord.copy(
            key = "key-3",
            value = objectMapper.createObjectNode().put("key", "processed"),
        )
        repo.insertNewRecord(processedRecord).also { repo.deletedOutboxRecord(it.id) }

        val result = repo.findUnprocessedRecords(10)

        result.map { it.key }.toSet() shouldContainAll setOf("key-1", "key-2")
    }

    @Test
    fun `markAsProcessed deletes record`() {
        val repo = OutboxRepository()
        val record = NewOutboxRecord(
            key = "key-4",
            valueType = "type-2",
            topic = "topic-2",
            value = objectMapper.createObjectNode().put("key", "to-process"),
        )
        val inserted = repo.insertNewRecord(record)
        repo.deletedOutboxRecord(inserted.id)

        repo.get(inserted.id).shouldBeNull()
    }

    @Test
    fun `markAsFailed updates record status, error message, and retry count`() {
        val repo = OutboxRepository()
        val record = NewOutboxRecord(
            key = "key-5",
            valueType = "type-3",
            topic = "topic-3",
            value = objectMapper.createObjectNode().put("key", "to-fail"),
        )
        val inserted = repo.insertNewRecord(record)
        val errorMsg = "Something went wrong"
        repo.markAsFailed(inserted.id, errorMsg)
        val failed = repo.get(inserted.id)
        failed?.status shouldBe OutboxRecordStatus.FAILED
        failed?.errorMessage shouldBe errorMsg
        failed?.retryCount shouldBe 1
    }
}
