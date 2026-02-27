package no.nav.amt.lib.kafka

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.lib.kafka.KafkaTestUtils.topicPartition1
import no.nav.amt.lib.kafka.KafkaTestUtils.topicPartition2
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OffsetManagerTest {
    private lateinit var sut: OffsetManager

    @BeforeEach
    fun setup() {
        sut = OffsetManager()
    }

    @Test
    fun `markProcessed sets uncommitted offset and clears retry`() {
        sut.markRetry(topicPartition1, 10)
        sut.markProcessed(topicPartition1, 42)

        val offsetsToCommit = sut.getOffsetsToCommit()

        offsetsToCommit shouldBe mapOf(topicPartition1 to OffsetAndMetadata(42))
        sut.getRetryOffsets() shouldBe emptyMap()
    }

    @Test
    fun `markRetry sets or updates retry offset`() {
        sut.markRetry(topicPartition1, 50)
        sut.markRetry(topicPartition1, 30)

        sut.getRetryOffsets() shouldBe mapOf(topicPartition1 to 30)
    }

    @Test
    fun `clearCommitted removes offsets`() {
        sut.markProcessed(topicPartition1, 100)
        sut.markProcessed(topicPartition2, 200)

        sut.clearCommitted(topicPartition1)

        sut.getOffsetsToCommit() shouldBe mapOf(topicPartition2 to OffsetAndMetadata(200))
    }

    @Test
    fun `clearRetry removes retry`() {
        sut.markRetry(topicPartition1, 10)
        sut.clearRetry(topicPartition1)

        sut.getRetryOffsets() shouldBe emptyMap()
    }

    @Nested
    inner class CommitTests {
        val consumer: KafkaConsumer<Any, Any> = mockk(relaxed = true)

        @Test
        fun `commit calls consumer and clears offsets`() {
            sut.markProcessed(topicPartition1, 123)
            sut.markProcessed(topicPartition2, 456)

            val expectedOffsets = sut.getOffsetsToCommit()
            expectedOffsets.size shouldBe 2

            sut.commit(consumer)

            verify { consumer.commitSync(expectedOffsets) }
            sut.getOffsetsToCommit() shouldBe emptyMap()
        }

        @Test
        fun `commit handles exception`() {
            sut.markProcessed(topicPartition1, 123)

            every {
                consumer.commitSync(any<Map<TopicPartition, OffsetAndMetadata>>())
            } throws RuntimeException("fail")

            shouldNotThrowAny {
                sut.commit(consumer)
            }

            sut.getOffsetsToCommit() shouldBe mapOf(topicPartition1 to OffsetAndMetadata(123))
        }
    }

    @Nested
    inner class RetryFailedPartitionsTests {
        val consumer = mockk<KafkaConsumer<Any, Any>>()

        @Test
        fun `retryFailedPartitions seeks correctly`() {
            every { consumer.seek(any<TopicPartition>(), any<Long>()) } just Runs
            every { consumer.position(topicPartition2) } returns 101L

            sut.markRetry(topicPartition2, 100L)
            sut.retryFailedPartitions(consumer)

            verify { consumer.seek(topicPartition2, 100L) }
        }

        @Test
        fun `retryFailedPartitions skips seek when offset is already greater than seek offset`() {
            every { consumer.position(topicPartition2) } returns 101L

            sut.markRetry(topicPartition2, 101L)

            verify(exactly = 0) { consumer.seek(topicPartition2, any<Long>()) }
        }
    }
}
