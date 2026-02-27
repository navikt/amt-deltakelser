package no.nav.amt.lib.kafka

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * A managed Kafka consumer that integrates with coroutines for structured concurrency.
 *
 * This consumer handles:
 * - Polling Kafka topics in a single-threaded dispatcher.
 * - Processing records via a suspending `consume` function.
 * - Tracking offsets for processed records ([OffsetManager]).
 * - Retrying failed records and applying backoff ([PartitionBackoffManager]).
 * - Pausing/resuming partitions based on backoff state ([PartitionPauseController]).
 * - Graceful shutdown using [KafkaConsumer.wakeup] and coroutine cancellation.
 *
 * Generics [K, V] represent the key and value types of the consumed Kafka records.
 *
 * @param K the type of the record key
 * @param V the type of the record value
 * @property topic the Kafka topic to consume from
 * @property config the Kafka consumer configuration
 * @property pollTimeoutMs the timeout for each poll in milliseconds (default 1000ms)
 * @property consume a suspending lambda that processes each record
 */
class ManagedKafkaConsumer<K, V>(
    private val topic: String,
    private val config: Map<String, *>,
    private val pollTimeoutMs: Long = 1000L,
    consume: suspend (key: K, value: V) -> Unit,
) : Consumer<K, V> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val running = AtomicBoolean(false)
    private lateinit var consumer: KafkaConsumer<K, V>

    private val partitionBackoffManager = PartitionBackoffManager()
    private val offsetManager = OffsetManager()
    private val partitionProcessor = PartitionProcessor(consume, partitionBackoffManager, offsetManager)
    private val pauseController = PartitionPauseController(partitionBackoffManager)

    // single-threaded KafkaConsumer dispatcher
    private val dispatcher = Executors
        .newSingleThreadExecutor { r -> Thread(r, "kafka-consumer-$topic") }
        .asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    /**
     * Starts the consumer.
     *
     * - Subscribes to the configured topic.
     * - Launches a coroutine to continuously poll and process records.
     * - If already running, logs a warning and does nothing.
     */
    override fun start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Consumer for $topic already started")
            return
        }

        log.info("Starting Kafka consumer for topic $topic")

        consumer = KafkaConsumer<K, V>(config).also { kafkaConsumer ->
            kafkaConsumer.subscribe(
                listOf(topic),
                ManagedConsumerRebalanceListener(
                    consumer = kafkaConsumer,
                    offsetManager = offsetManager,
                    backoffManager = partitionBackoffManager,
                ),
            )

            scope.launch {
                kafkaConsumer.use { runLoop(it) }
            }
        }
    }

    /**
     * Stops the consumer gracefully.
     *
     * - Sets the running flag to false.
     * - Wakes up the Kafka consumer if it is polling.
     * - Cancels and joins the consumer coroutine.
     * - Closes the dispatcher.
     */
    override suspend fun close() {
        if (!running.compareAndSet(true, false)) return

        log.info("Stopping Kafka consumer for topic $topic")

        consumer.wakeup()
        scope.coroutineContext[Job]?.cancelAndJoin()
        dispatcher.close()
    }

    /**
     * Runs the main polling loop.
     *
     * Continuously polls the Kafka topic and processes records using [pollOnce].
     * Handles shutdown via [WakeupException] and coroutine cancellation.
     *
     * @param consumer the KafkaConsumer instance to use
     */
    private suspend fun runLoop(consumer: KafkaConsumer<K, V>) {
        try {
            while (running.get()) pollOnce(consumer)
        } catch (_: WakeupException) {
            log.info("Consumer for $topic shutting down")
        } catch (ce: CancellationException) {
            log.info("Consumer coroutine cancelled for $topic")
            throw ce
        } catch (throwable: Throwable) {
            log.error("Unexpected error in consumer loop for $topic", throwable)
        } finally {
            offsetManager.commit(consumer)
        }
    }

    /**
     * Polls Kafka for records once and processes them.
     *
     * - Retries any failed partitions.
     * - Pauses/resumes partitions based on backoff state.
     * - Processes records using [PartitionProcessor].
     * - Commits offsets after processing.
     *
     * @param consumer the KafkaConsumer instance to poll from
     */
    private suspend fun pollOnce(consumer: KafkaConsumer<K, V>) {
        offsetManager.retryFailedPartitions(consumer)
        pauseController.update(consumer)

        val records = consumer.poll(Duration.ofMillis(pollTimeoutMs))
        if (records.isEmpty) return

        log.info("Consumer for $topic polled ${records.count()} records")

        records.partitions().forEach { topicPartition ->
            partitionProcessor.process(
                topicPartition = topicPartition,
                records = records.records(topicPartition),
            )
        }

        offsetManager.commit(consumer)
    }
}
