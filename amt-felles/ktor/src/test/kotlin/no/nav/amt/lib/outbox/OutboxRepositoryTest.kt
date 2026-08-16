package no.nav.amt.lib.outbox

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class OutboxRepositoryTest {
    companion object {
        @RegisterExtension
        private val dbExtension = DatabaseTestExtension()
    }

    private val outboxRepository = OutboxRepository()

    @Test
    fun `insertNewRecord returns record with non-null id`() {
        // Arrange
        val record = NewOutboxRecord(
            key = "test-key",
            valueType = "test-value-type",
            topic = "test-topic",
            value = objectMapper.createObjectNode().put("key", "value"),
        )

        // Act
        val insertedRecord = outboxRepository.insertNewRecord(record)

        // Assert
        assertSoftly(insertedRecord) {
            id shouldNotBe null
            key shouldBe record.key
            valueType shouldBe record.valueType
            topic shouldBe record.topic
            value shouldBe record.value
        }
    }

    @Test
    fun `findUnprocessedRecords returns pending and failed records`() {
        // Arrange
        val pendingRecord = NewOutboxRecord(
            key = "key-1",
            valueType = "type-1",
            topic = "topic-1",
            value = objectMapper.createObjectNode().put("key", "pending"),
        )
        outboxRepository.insertNewRecord(pendingRecord)

        val failedRecord = pendingRecord.copy(
            key = "key-2",
            value = objectMapper.createObjectNode().put("key", "failed"),
        )
        outboxRepository.insertNewRecord(failedRecord).also {
            outboxRepository.markAsFailed(it.id, "Some error")
        }

        val processedRecord = pendingRecord.copy(
            key = "key-3",
            value = objectMapper.createObjectNode().put("key", "processed"),
        )

        val processedInserted = outboxRepository.insertNewRecord(processedRecord)
        outboxRepository.deleteOutboxRecord(processedInserted.id)

        // Act
        val result = outboxRepository.findUnprocessedRecords(10)

        // Assert
        val resultKeys = result.map { it.key }.toSet()
        resultKeys.size shouldBe 2
        resultKeys shouldContainAll setOf("key-1", "key-2")
        resultKeys.contains("key-3") shouldBe false
    }

    @Test
    fun `deleteOutboxRecord deletes record`() {
        // Arrange
        val record = NewOutboxRecord(
            key = "key-4",
            valueType = "type-2",
            topic = "topic-2",
            value = objectMapper.createObjectNode().put("key", "to-process"),
        )
        val inserted = outboxRepository.insertNewRecord(record)

        // Act
        outboxRepository.deleteOutboxRecord(inserted.id)

        // Assert
        outboxRepository.get(inserted.id).shouldBeNull()
    }

    @Test
    fun `markAsFailed updates record status, error message, and retry count`() {
        // Arrange
        val record = NewOutboxRecord(
            key = "key-5",
            valueType = "type-3",
            topic = "topic-3",
            value = objectMapper.createObjectNode().put("key", "to-fail"),
        )
        val inserted = outboxRepository.insertNewRecord(record)
        val errorMsg = "Something went wrong"

        // Act
        outboxRepository.markAsFailed(
            recordId = inserted.id,
            errorMessage = errorMsg,
        )

        // Assert
        assertSoftly(outboxRepository.get(inserted.id).shouldNotBeNull()) {
            status shouldBe OutboxRecordStatus.FAILED
            errorMessage shouldBe errorMsg
            retryCount shouldBe 1
        }
    }
}
