package no.nav.amt.lib.kafka

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory

/**
 * Manages Kafka consumer offsets for processed and retriable records.
 *
 * Tracks offsets that have been processed but not yet committed (`uncommittedOffsets`),
 * and offsets for records that failed processing and need to be retried (`retryOffsets`).
 * Provides methods for marking records as processed or needing retry,
 * committing offsets to Kafka, and seeking retry offsets in the consumer.
 */
internal class OffsetManager {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Offsets that have been processed but not yet committed */
    private val uncommittedOffsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()

    /** Offsets for partitions that need to be retried */
    private val retryOffsets = mutableMapOf<TopicPartition, Long>()

    /**
     * Marks a record as successfully processed.
     *
     * Removes any retry offset for the partition and stores the latest uncommitted offset.
     *
     * @param tp the topic partition of the record
     * @param offset the offset of the processed record (next offset to commit)
     */
    fun markProcessed(tp: TopicPartition, offset: Long) {
        uncommittedOffsets[tp] = OffsetAndMetadata(offset)
        retryOffsets.remove(tp)
    }

    /**
     * Marks a record for retry due to processing failure.
     *
     * Stores the lowest offset for the partition that still needs to be retried.
     *
     * @param tp the topic partition of the record
     * @param offset the offset to retry from
     */
    fun markRetry(tp: TopicPartition, offset: Long) {
        retryOffsets[tp] = retryOffsets[tp]
            ?.coerceAtMost(offset)
            ?: offset
    }

    /** Returns a snapshot of partitions and offsets that need to be retried */
    fun getRetryOffsets(): Map<TopicPartition, Long> = retryOffsets.toMap()

    /** Returns a snapshot of partitions and offsets that are ready to commit */
    fun getOffsetsToCommit(): Map<TopicPartition, OffsetAndMetadata> = uncommittedOffsets.toMap()

    /** Clears committed offsets for a given partition */
    fun clearCommitted(tp: TopicPartition) = uncommittedOffsets.remove(tp)

    /** Clears retry offsets for a given partition */
    fun clearRetry(tp: TopicPartition) = retryOffsets.remove(tp)

    /**
     * Seeks the consumer to offsets that need to be retried.
     *
     * For each partition in [retryOffsets], the consumer's position is set to the retry offset.
     * Logs warnings if the partition is not assigned to this consumer.
     *
     * @param consumer the KafkaConsumer to seek
     */
    fun retryFailedPartitions(consumer: KafkaConsumer<*, *>) = retryOffsets.forEach { (tp, offset) ->
        try {
            val current = consumer.position(tp)
            if (current != offset) consumer.seek(tp, offset)
            log.debug("Retrying {} from offset {} (was {})", tp, offset, current)
        } catch (e: IllegalStateException) {
            log.warn("Partition $tp not assigned during retry seek", e)
        }
    }

    /**
     * Commits all uncommitted offsets to Kafka synchronously.
     *
     * Clears the uncommitted offsets after a successful commit.
     * Logs an error if the commit fails.
     *
     * @param consumer the KafkaConsumer to commit offsets for
     */
    fun commit(consumer: KafkaConsumer<*, *>) {
        val offsetsToCommit = getOffsetsToCommit()
        if (offsetsToCommit.isEmpty()) return
        try {
            consumer.commitSync(offsetsToCommit)
            log.info("Offsets committed: $offsetsToCommit")
            uncommittedOffsets.clear()
        } catch (e: Exception) {
            log.error("Commit failed for offsets $offsetsToCommit", e)
        }
    }
}
