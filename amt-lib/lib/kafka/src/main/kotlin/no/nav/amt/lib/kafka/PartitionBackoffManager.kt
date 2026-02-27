package no.nav.amt.lib.kafka

import no.nav.amt.lib.kafka.config.MAX_POLL_INTERVAL_MS
import org.apache.kafka.common.TopicPartition
import kotlin.math.min

/**
 * Manages retry backoff state for Kafka partitions.
 *
 * Tracks the number of consecutive failures per partition and calculates
 * a backoff period before the partition should be retried.
 *
 * Used by the consumer to temporarily pause processing of partitions that
 * repeatedly fail, avoiding tight retry loops that could overwhelm the system.
 */
internal class PartitionBackoffManager {
    /** Internal state for a partition, holding retry count and backoff expiration timestamp */
    private data class PartitionState(
        val retryCount: Int,
        val backoffUntil: Long,
    )

    /** Map of topic partitions to their current retry state */
    private val state = mutableMapOf<TopicPartition, PartitionState>()

    /**
     * Increments the retry count for a partition and calculates a new backoff period.
     *
     * @param tp the partition for which to increment the retry count
     */
    fun incrementRetryCount(tp: TopicPartition) {
        val newRetryCount = (state[tp]?.retryCount ?: 0) + 1

        state[tp] = PartitionState(
            retryCount = newRetryCount,
            backoffUntil = calculateBackoffUntilMs(newRetryCount),
        )
    }

    /**
     * Resets the retry count and backoff state for a partition.
     *
     * @param tp the partition to reset
     */
    fun resetRetryCount(tp: TopicPartition) {
        state.remove(tp)
    }

    /**
     * Checks whether a partition is currently in backoff.
     *
     * @param tp the partition to check
     * @return true if the partition should not be retried yet
     */
    fun isInBackoff(tp: TopicPartition): Boolean {
        val until = state[tp]?.backoffUntil ?: return false
        return System.currentTimeMillis() < until
    }

    /**
     * Calculates the timestamp until which a partition should remain in backoff
     * based on the retry count.
     *
     * @param retryCount the number of consecutive failures
     * @return epoch milliseconds indicating when the backoff ends
     */
    internal fun calculateBackoffUntilMs(retryCount: Int): Long =
        System.currentTimeMillis() + min(COEFFICIENT * retryCount * retryCount + BASE_DELAY_MS, MAX_DELAY)

    /**
     * Returns the current retry count for a partition.
     *
     * @param tp the partition to check
     * @return the number of consecutive failures
     */
    internal fun getRetryCount(tp: TopicPartition) = state[tp]?.retryCount ?: 0

    companion object {
        private const val COEFFICIENT = 500L
        private const val BASE_DELAY_MS = 1_000L
        const val MAX_DELAY = MAX_POLL_INTERVAL_MS - 60_000L
    }
}
