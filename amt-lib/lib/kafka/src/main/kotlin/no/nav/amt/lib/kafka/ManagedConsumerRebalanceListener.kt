package no.nav.amt.lib.kafka

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory

/**
 * A Kafka [ConsumerRebalanceListener] that handles partition revokes and assignments
 * in a managed consumer setup with offset tracking and retry/backoff management.
 *
 * On partition revocation, it attempts to commit any pending offsets for the revoked partitions
 * and clears their state from [OffsetManager] and [PartitionBackoffManager].
 * On partition assignment, it simply logs the assigned partitions.
 *
 * @param K the type of the record key
 * @param V the type of the record value
 * @property consumer the underlying [KafkaConsumer] used for committing offsets
 * @property offsetManager manages offsets for processed and retriable records
 * @property backoffManager manages retry counts and backoff logic for partitions
 */
internal class ManagedConsumerRebalanceListener<K, V>(
    private val consumer: KafkaConsumer<K, V>,
    private val offsetManager: OffsetManager,
    private val backoffManager: PartitionBackoffManager,
) : ConsumerRebalanceListener {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Called before a rebalance operation, when partitions are revoked from this consumer.
     *
     * Attempts to commit any offsets for revoked partitions and clears their state
     * from the [OffsetManager] and [PartitionBackoffManager] to avoid leaking state.
     *
     * @param revokedPartitions the partitions that are being revoked from this consumer
     */
    override fun onPartitionsRevoked(revokedPartitions: Collection<TopicPartition>) {
        log.info("Partitions revoked: $revokedPartitions")

        // collect offsets for revoked partitions that are pending commit
        val offsetsToCommitDuringRebalance = offsetManager
            .getOffsetsToCommit()
            .filterKeys { it in revokedPartitions }

        // try to commit offsets before losing ownership
        try {
            if (offsetsToCommitDuringRebalance.isNotEmpty()) {
                consumer.commitSync(offsetsToCommitDuringRebalance)
                log.info("Committing offsets before revoke: $offsetsToCommitDuringRebalance")
            }
        } catch (e: Exception) {
            // log but continue; the new owner will retry from last committed offsets
            log.error("Failed to commit offsets during partition revoke for $revokedPartitions: $offsetsToCommitDuringRebalance", e)
        } finally {
            // remove committed/uncommitted state for revoked partitions
            revokedPartitions.forEach { tp ->
                offsetManager.clearCommitted(tp)
                offsetManager.clearRetry(tp)
                backoffManager.resetRetryCount(tp)
            }
        }
    }

    /**
     * Called after a rebalance operation, when partitions are assigned to this consumer.
     *
     * Currently only logs the assigned partitions.
     *
     * @param partitions the partitions assigned to this consumer
     */
    override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
        log.info("Partitions assigned: $partitions")
    }
}
