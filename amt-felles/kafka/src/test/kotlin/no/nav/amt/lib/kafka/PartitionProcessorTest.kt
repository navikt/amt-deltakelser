package no.nav.amt.lib.kafka

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.lib.kafka.KafkaTestUtils.topicPartition1
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PartitionProcessorTest {
    private val consume = mockk<suspend (String, String) -> Unit>()
    private val backoffManager = mockk<PartitionBackoffManager>(relaxed = true)
    private val offsetManager = mockk<OffsetManager>(relaxed = true)

    private lateinit var sut: PartitionProcessor<String, String>

    @BeforeEach
    fun setup() {
        coEvery { consume(any(), any()) } just Runs
        coEvery { offsetManager.getRetryOffsets() } returns emptyMap()
    }

    companion object {
        fun createTestRecord(
            topic: String = "test-topic",
            key: String = "key",
            value: String = "value",
            partition: Int = 0,
            offset: Long = 0L,
        ): ConsumerRecord<String, String> = mockk(relaxed = true) {
            coEvery { this@mockk.topic() } returns topic
            coEvery { this@mockk.key() } returns key
            coEvery { this@mockk.value() } returns value
            coEvery { this@mockk.partition() } returns partition
            coEvery { this@mockk.offset() } returns offset
        }
    }

    @Nested
    inner class NormalProcessingTests {
        @Test
        fun `skal prosessere record og markere som prosessert`() = runTest {
            // Arrange
            sut = PartitionProcessor(
                consume = consume,
                backoffManager = backoffManager,
                offsetManager = offsetManager,
                skipFilter = { false },
            )

            val records = listOf(
                createTestRecord(key = "key1", value = "value1", offset = 100L),
            )

            // Act
            sut.process(topicPartition1, records)

            // Assert
            coVerify { consume("key1", "value1") }
            coVerify { offsetManager.markProcessed(topicPartition1, 101L) }
        }

        @Test
        fun `skal stoppe ved første feil`() = runTest {
            // Arrange
            coEvery { consume("key2", any()) } throws RuntimeException("Processing failed")

            sut = PartitionProcessor(
                consume = consume,
                backoffManager = backoffManager,
                offsetManager = offsetManager,
                skipFilter = { false },
            )

            val records = listOf(
                createTestRecord(key = "key1", value = "value1", offset = 100L),
                createTestRecord(key = "key2", value = "value2", offset = 101L),
                createTestRecord(key = "key3", value = "value3", offset = 102L),
            )

            // Act
            sut.process(topicPartition1, records)

            // Assert
            coVerify(exactly = 1) { consume("key1", any()) }
            coVerify(exactly = 1) { consume("key2", any()) }
            coVerify(exactly = 0) { consume("key3", any()) }
            coVerify { offsetManager.markRetry(topicPartition1, 101L) }
        }
    }

    @Nested
    inner class SkipFilterTests {
        @Test
        fun `skal skippe record når skipFilter returnerer true`() = runTest {
            // Arrange
            sut = PartitionProcessor(
                consume = consume,
                backoffManager = backoffManager,
                offsetManager = offsetManager,
                skipFilter = { it.key() == "skip-me" },
            )

            val records = listOf(
                createTestRecord(key = "skip-me", value = "value1", offset = 100L),
            )

            // Act
            sut.process(topicPartition1, records)

            // Assert
            coVerify(exactly = 0) { consume(any(), any()) }
            coVerify { offsetManager.markProcessed(topicPartition1, 101L) }
        }

        @Test
        fun `skal prosessere record når skipFilter returnerer false`() = runTest {
            // Arrange
            sut = PartitionProcessor(
                consume = consume,
                backoffManager = backoffManager,
                offsetManager = offsetManager,
                skipFilter = { it.key() == "skip-me" },
            )

            val records = listOf(
                createTestRecord(key = "process-me", value = "value1", offset = 100L),
            )

            // Act
            sut.process(topicPartition1, records)

            // Assert
            coVerify { consume("process-me", "value1") }
            coVerify { offsetManager.markProcessed(topicPartition1, 101L) }
        }
    }

    @Nested
    inner class SkipFilterErrorHandlingTests {
        @Test
        fun `skal håndtere exception fra skipFilter og prosessere record normalt`() = runTest {
            // Arrange
            sut = PartitionProcessor(
                consume = consume,
                backoffManager = backoffManager,
                offsetManager = offsetManager,
                skipFilter = { throw IllegalArgumentException("skipFilter error") },
            )

            val records = listOf(
                createTestRecord(key = "key1", value = "value1", offset = 100L),
            )

            // Act
            sut.process(topicPartition1, records)

            // Assert
            coVerify { consume("key1", "value1") }
            coVerify { offsetManager.markProcessed(topicPartition1, 101L) }
        }

        @Test
        fun `skal håndtere exception fra skipFilter med multiple records`() = runTest {
            // Arrange
            var callCount = 0
            sut = PartitionProcessor(
                consume = consume,
                backoffManager = backoffManager,
                offsetManager = offsetManager,
                skipFilter = {
                    callCount++
                    if (callCount == 1) throw RuntimeException("skipFilter failed")
                    false
                },
            )

            val records = listOf(
                createTestRecord(key = "key1", value = "value1", offset = 100L),
                createTestRecord(key = "key2", value = "value2", offset = 101L),
            )

            // Act
            sut.process(topicPartition1, records)

            // Assert
            coVerify { consume("key1", "value1") }
            coVerify { consume("key2", "value2") }
            coVerify { offsetManager.markProcessed(topicPartition1, 101L) }
            coVerify { offsetManager.markProcessed(topicPartition1, 102L) }
        }

        @Test
        fun `skal håndtere exception fra skipFilter uten å påvirke retry logikk`() = runTest {
            // Arrange
            sut = PartitionProcessor(
                consume = consume,
                backoffManager = backoffManager,
                offsetManager = offsetManager,
                skipFilter = { throw RuntimeException("skipFilter error") },
            )

            coEvery { consume("key1", "value1") } throws RuntimeException("Processing failed")

            val records = listOf(
                createTestRecord(key = "key1", value = "value1", offset = 100L),
            )

            // Act
            sut.process(topicPartition1, records)

            // Assert
            coVerify { offsetManager.markRetry(topicPartition1, 100L) }
            coVerify { backoffManager.incrementRetryCount(topicPartition1) }
        }
    }

    @Nested
    inner class ResetRetryCountTests {
        @Test
        fun `skal resette retry count når alle records ble prosessert`() = runTest {
            // Arrange
            sut = PartitionProcessor(
                consume = consume,
                backoffManager = backoffManager,
                offsetManager = offsetManager,
                skipFilter = { false },
            )

            val records = listOf(
                createTestRecord(key = "key1", value = "value1", offset = 100L),
            )

            // Act
            sut.process(topicPartition1, records)

            // Assert
            coVerify { backoffManager.resetRetryCount(topicPartition1) }
        }

        @Test
        fun `skal ikke resette retry count når det er retry offsets`() = runTest {
            // Arrange
            coEvery { offsetManager.getRetryOffsets() } returns mapOf(topicPartition1 to 100L)

            sut = PartitionProcessor(
                consume = consume,
                backoffManager = backoffManager,
                offsetManager = offsetManager,
                skipFilter = { false },
            )

            coEvery { consume("key1", "value1") } throws RuntimeException("fail")

            val records = listOf(
                createTestRecord(key = "key1", value = "value1", offset = 100L),
            )

            // Act
            sut.process(topicPartition1, records)

            // Assert
            coVerify(exactly = 0) { backoffManager.resetRetryCount(topicPartition1) }
        }
    }
}
