package no.nav.amt.lib.kafka

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

/**
 * Controls pausing and resuming of Kafka partitions based on backoff state.
 *
 * Works together with [PartitionBackoffManager] to temporarily pause partitions
 * that have experienced consecutive failures, preventing tight retry loops.
 *
 * Periodically called in the consumer loop to adjust which partitions are paused.
 */
internal class PartitionPauseController(
    private val backoffManager: PartitionBackoffManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Updates the paused state of partitions in the given Kafka consumer.
     *
     * - Partitions currently in backoff that are not already paused are paused.
     * - Partitions previously paused but no longer in backoff are resumed.
     *
     * @param consumer the KafkaConsumer whose partition pause state is to be updated
     */
    fun update(consumer: KafkaConsumer<*, *>) {
        // split partitions into those that can be processed and those that are in backoff
        val (backoffPartitions, processable) = consumer
            .assignment()
            .partition { tp -> backoffManager.isInBackoff(tp) }

        // get current paused partitions
        val paused = consumer.paused()

        // pause partitions that are in backoff and not already paused
        backoffPartitions
            .filterNot { it in paused }
            .takeIf { it.isNotEmpty() }
            ?.let { topicPartitions ->
                consumer.pause(topicPartitions)
                topicPartitions.forEach { tp ->
                    runCatching { consumer.position(tp) }
                        .onSuccess { position ->
                            log.warn("Partition $tp entering retry at next offset $position")
                        }.onFailure {
                            log.warn("Partition $tp entering retry, but position unavailable (rebalance in progress)")
                        }
                }
            }

        // resume paused partitions that are no longer in backoff
        processable
            .filter { it in paused }
            .takeIf { it.isNotEmpty() }
            ?.let { consumer.resume(it) }
    }
}
