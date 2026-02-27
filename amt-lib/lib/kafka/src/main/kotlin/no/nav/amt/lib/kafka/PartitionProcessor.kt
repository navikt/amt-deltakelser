package no.nav.amt.lib.kafka

import kotlinx.coroutines.CancellationException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory

/**
 * Processes Kafka records for a single partition.
 *
 * Responsible for invoking the consumer function for each record,
 * marking offsets as processed, and handling retries and backoff
 * for partitions that encounter processing failures.
 *
 * Ensures that partitions with failures are paused and retried
 * according to [PartitionBackoffManager] rules, and that offsets
 * are tracked correctly in [OffsetManager].
 *
 * @param K the type of the record key
 * @param V the type of the record value
 * @property consume the suspending consumer function to process each record
 * @property backoffManager manages retry count and backoff per partition
 * @property offsetManager tracks offsets for processed and retryable records
 */
internal class PartitionProcessor<K, V>(
    private val consume: suspend (K, V) -> Unit,
    private val backoffManager: PartitionBackoffManager,
    private val offsetManager: OffsetManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Processes a batch of records for a given partition.
     *
     * - Invokes [consume] for each record.
     * - Marks offsets as processed in [OffsetManager] if processing succeeds.
     * - On failure, marks the record for retry and increments the partition's backoff count.
     * - Stops processing the partition on the first failure in the batch.
     * - Resets backoff if all records were successfully processed.
     *
     * @param topicPartition the partition of the records being processed
     * @param records the list of records to process
     * @throws CancellationException if the coroutine is canceled during processing
     */
    suspend fun process(topicPartition: TopicPartition, records: List<ConsumerRecord<K, V>>) {
        for (record in records) {
            val recordInfo = "topic=${record.topic()} key=${record.key()} " +
                "partition=${record.partition()} offset=${record.offset()}"

            try {
                val start = System.currentTimeMillis()
                consume(record.key(), record.value())
                offsetManager.markProcessed(topicPartition, record.offset() + 1)
                log.info("Consumed record in ${System.currentTimeMillis() - start} ms: $recordInfo")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                log.warn("Failed processing record: $recordInfo", t)
                offsetManager.markRetry(topicPartition, record.offset())
                backoffManager.incrementRetryCount(topicPartition)
                break // stop on first failure in partition
            }
        }

        if (topicPartition !in offsetManager.getRetryOffsets()) {
            backoffManager.resetRetryCount(topicPartition)
        }
    }
}
