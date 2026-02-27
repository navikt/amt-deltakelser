package no.nav.amt.lib.kafka

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.lib.kafka.KafkaTestUtils.topicPartition1
import no.nav.amt.lib.kafka.KafkaTestUtils.topicPartition2
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ManagedConsumerRebalanceListenerTest {
    private val consumer: KafkaConsumer<String, String> = mockk(relaxed = true)
    private val offsetManager: OffsetManager = mockk(relaxed = true)
    private val backoffManager: PartitionBackoffManager = mockk(relaxed = true)
    private val listener = ManagedConsumerRebalanceListener(consumer, offsetManager, backoffManager)

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `onPartitionsRevoked commits offsets and clears state`() {
        val offsetsToCommit = mapOf<TopicPartition, OffsetAndMetadata>(topicPartition1 to mockk(), topicPartition2 to mockk())
        every { offsetManager.getOffsetsToCommit() } returns offsetsToCommit

        listener.onPartitionsRevoked(listOf(topicPartition1, topicPartition2))

        verify { consumer.commitSync(offsetsToCommit) }

        verify { offsetManager.clearCommitted(topicPartition1) }
        verify { offsetManager.clearCommitted(topicPartition2) }

        verify { offsetManager.clearRetry(topicPartition1) }
        verify { offsetManager.clearRetry(topicPartition2) }

        verify { backoffManager.resetRetryCount(topicPartition1) }
        verify { backoffManager.resetRetryCount(topicPartition2) }
    }

    @Test
    fun `onPartitionsAssigned logs assigned partitions`() {
        shouldNotThrowAny {
            listener.onPartitionsAssigned(listOf(topicPartition1, topicPartition2))
        }
    }
}
