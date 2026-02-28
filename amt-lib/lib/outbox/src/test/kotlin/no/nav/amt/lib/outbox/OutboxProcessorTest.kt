package no.nav.amt.lib.outbox

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.outbox.metrics.PrometheusOutboxMeter
import no.nav.amt.lib.outbox.utils.assertProduced
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.TestPostgresContainer
import no.nav.amt.lib.utils.job.JobManager
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class OutboxProcessorTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupAll() = TestPostgresContainer.bootstrap()
    }

    private val prometheusRegistry = PrometheusRegistry()
    private val outboxRepository = OutboxRepository()
    private val outboxService = OutboxService(PrometheusOutboxMeter(prometheusRegistry))

    private val kafkaConfig = LocalKafkaConfig(SingletonKafkaProvider.getHost())
    private val kafkaProducer = Producer<String, String>(kafkaConfig)

    private val outboxProcessor =
        OutboxProcessor(
            outboxService = outboxService,
            jobManager = JobManager({ true }, { true }),
            producer = kafkaProducer,
        )

    private val testTopic = "outbox-test-topic"
    private val failingTestTopic = "INVALID TOPIC NAME!"

    @Test
    fun `processRecords - new record - gets_processed`() {
        val record = newRecord()
        outboxProcessor.processRecords()

        outboxRepository.get(record.id).shouldBeNull()

        verifyProducedRecord(record)
    }

    @Test
    fun `processRecords - producer fails - marks record as FAILED`() {
        val record = newRecord(topic = failingTestTopic)

        outboxProcessor.processRecords()

        val failedRecord = outboxRepository.get(record.id)!!
        failedRecord.status shouldBe OutboxRecordStatus.FAILED
    }

    @Test
    fun `processRecords - previous record failed - skips new record for same aggregate`() {
        val key = UUID.randomUUID()

        val recordToFail = newRecord(key = key, topic = failingTestTopic)
        val recordToIgnore = newRecord(key = key, topic = failingTestTopic)

        outboxProcessor.processRecords()
        outboxRepository.get(recordToFail.id)!!.status shouldBe OutboxRecordStatus.FAILED
        outboxRepository.get(recordToIgnore.id)!!.status shouldBe OutboxRecordStatus.PENDING
    }

    @Test
    fun `processRecords - multiple records - all processRecords successfully`() {
        val records = newRecords(5)

        outboxProcessor.processRecords()

        records.forEach { record ->
            outboxRepository.get(record.id).shouldBeNull()
            verifyProducedRecord(record)
        }
    }

    @Test
    fun `processRecords - mixed success and failure - handles both correctly`() {
        val successRecords = newRecords(3, testTopic)
        val failRecords = newRecords(2, failingTestTopic)

        outboxProcessor.processRecords()

        successRecords.forEach { record ->
            outboxRepository.get(record.id).shouldBeNull()
        }

        failRecords.forEach { record ->
            val failedRecord = outboxRepository.get(record.id)!!
            failedRecord.status shouldBe OutboxRecordStatus.FAILED
        }
    }

    @Test
    fun `processRecords - failed records are retried on next run`() {
        val record = newRecord(topic = failingTestTopic)

        outboxProcessor.processRecords()
        val firstResult = outboxRepository.get(record.id)!!
        firstResult.status shouldBe OutboxRecordStatus.FAILED
        val firstRetryCount = firstResult.retryCount

        outboxProcessor.processRecords()
        val secondResult = outboxRepository.get(record.id)!!
        secondResult.status shouldBe OutboxRecordStatus.FAILED
        secondResult.retryCount shouldBe (firstRetryCount + 1)
    }

    @Test
    fun `process - same aggregate different topics - processRecords independently`() {
        val key = UUID.randomUUID()

        val failingRecord = newRecord(key = key, topic = failingTestTopic)
        val successRecord = newRecord(key = key, topic = testTopic)

        outboxProcessor.processRecords()

        outboxRepository.get(failingRecord.id).shouldNotBeNull().status shouldBe OutboxRecordStatus.FAILED
        outboxRepository.get(successRecord.id).shouldBeNull()
        verifyProducedRecord(successRecord)
    }

    private data class Value(
        val name: String = "Test",
        val values: List<Int> = listOf(1, 2, 3),
    )

    private fun newRecord(
        value: Any = Value(),
        key: UUID = UUID.randomUUID(),
        topic: String = testTopic,
    ) = outboxService.insertRecord(
        key = key,
        value = value,
        topic = topic,
    )

    private fun newRecords(
        count: Int,
        topic: String = testTopic,
        key: UUID? = null,
    ) = (1..count).map {
        newRecord(
            value = Value("Test-$it"),
            key = key ?: UUID.randomUUID(),
            topic = topic,
        )
    }

    private fun verifyProducedRecord(
        record: OutboxRecord,
        topic: String = testTopic,
    ) = assertProduced(topic) {
        eventually {
            val value = objectMapper.readTree(it[record.key])

            value shouldBe record.value
        }
    }
}
