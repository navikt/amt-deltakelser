package no.nav.tiltaksarrangor.melding

import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import no.nav.common.kafka.producer.feilhandtering.StoredProducerRecord
import no.nav.common.kafka.producer.feilhandtering.publisher.BatchedKafkaProducerRecordPublisher
import no.nav.common.kafka.producer.feilhandtering.util.KafkaProducerRecordProcessorBuilder
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import no.nav.tiltaksarrangor.IntegrationTestBase
import no.nav.tiltaksarrangor.melding.endring.EndringService
import no.nav.tiltaksarrangor.melding.endring.request.LeggTilOppstartsdatoRequest
import no.nav.tiltaksarrangor.melding.forslag.forlengDeltakelseForslag
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class KafkaOutboxTransactionTest(
    private val endringService: EndringService,
    private val meldingProducer: MeldingProducer,
    private val transactionTemplate: TransactionTemplate,
    private val meterRegistry: MeterRegistry,
    private val producerRepository: PostgresJdbcTemplateProducerRepository,
) : IntegrationTestBase() {
    @Test
    fun `produsent krever en aktiv database transaksjon`() {
        // Arrange
        val forslag = forlengDeltakelseForslag()

        // Act
        assertThrows<IllegalTransactionStateException> {
            meldingProducer.produce(forslag)
        }

        // Assert
        jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") shouldBe 0
    }

    @Test
    fun `lagrer melding med eksisterende topic nøkkel og payload i outbox`() {
        // Arrange
        val forslag = forlengDeltakelseForslag()

        // Act
        transactionTemplate.executeWithoutResult {
            meldingProducer.produce(forslag)
        }

        // Assert
        val record = jdbcTemplate
            .query(
                "SELECT topic, key, value FROM kafka_producer_record",
            ) { rs, _ ->
                Triple(
                    rs.getString("topic"),
                    rs.getBytes("key").toString(Charsets.UTF_8),
                    objectMapper.readValue<Forslag>(rs.getBytes("value").toString(Charsets.UTF_8)),
                )
            }.single()

        record.first shouldBe MELDING_TOPIC
        record.second shouldBe forslag.id.toString()
        record.third shouldBe forslag
        meterRegistry.get("amt_tiltaksarrangor_bff_kafka_outbox_ventende").gauge().value() shouldBe 1.0
    }

    @Test
    fun `beholder feilet outboxmelding og sletter den etter vellykket retry`() {
        // Arrange
        producerRepository.storeRecord(
            StoredProducerRecord(
                MELDING_TOPIC,
                "outbox-key".toByteArray(),
                "outbox-value".toByteArray(),
                "[]",
            ),
        )
        val producerClient = mockk<KafkaProducerClient<ByteArray, ByteArray>>(relaxUnitFun = true)
        val producer = mockk<Producer<ByteArray, ByteArray>>(relaxUnitFun = true)
        val retryStarted = CountDownLatch(1)
        val allowRetryToSucceed = CountDownLatch(1)
        val publishAttempts = AtomicInteger()

        every { producerClient.getProducer() } returns producer
        every { producerClient.send(any(), any()) } answers {
            if (publishAttempts.incrementAndGet() == 1) {
                secondArg<Callback>().onCompletion(null, RuntimeException("Kafka unavailable"))
            } else {
                retryStarted.countDown()
                check(allowRetryToSucceed.await(5, TimeUnit.SECONDS))
                secondArg<Callback>().onCompletion(mockk<RecordMetadata>(), null)
            }
            CompletableFuture.completedFuture(mockk<RecordMetadata>())
        }

        val processor = createOutboxProcessor(producerClient)
        try {
            // Act
            processor.start()

            // Assert
            verify(timeout = 5_000) { producerClient.send(any(), any()) }
            check(retryStarted.await(5, TimeUnit.SECONDS)) { "Outbox record was not retried" }
            jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") shouldBe 1

            allowRetryToSucceed.countDown()

            verify(timeout = 5_000, atLeast = 2) { producerClient.send(any(), any()) }
            awaitOutboxCount(0) shouldBe true
            publishAttempts.get() shouldBe 2
        } finally {
            allowRetryToSucceed.countDown()
            processor.close()
        }
    }

    @Test
    fun `databaseendring og outboxmelding rulles tilbake sammen`() {
        with(DeltakerContext(applicationContext)) {
            // Arrange
            setVenterPaOppstart()
            val original = deltakerRepository.getDeltaker(deltaker.id)
            val request = LeggTilOppstartsdatoRequest(
                startdato = LocalDate.now().plusWeeks(7),
                sluttdato = LocalDate.now().plusWeeks(42),
            )
            jdbcTemplate.execute(
                "ALTER TABLE kafka_producer_record ADD CONSTRAINT test_outbox_insert_failure CHECK (false)",
            )

            try {
                // Act
                assertThrows<DataIntegrityViolationException> {
                    endringService.endreDeltaker(deltaker, deltakerliste, koordinator, request)
                }
            } finally {
                jdbcTemplate.execute("ALTER TABLE kafka_producer_record DROP CONSTRAINT test_outbox_insert_failure")
            }

            // Assert
            deltakerRepository.getDeltaker(deltaker.id) shouldBe original
            jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") shouldBe 0
        }
    }

    private fun createOutboxProcessor(producerClient: KafkaProducerClient<ByteArray, ByteArray>): KafkaProducerRecordProcessor =
        KafkaProducerRecordProcessorBuilder
            .builder()
            .withProducerRepository(producerRepository)
            .withRecordPublisher(BatchedKafkaProducerRecordPublisher(producerClient))
            .withLeaderElectionClient(LeaderElectionClient { true })
            .withTopicWhitelist(listOf(MELDING_TOPIC))
            .withRecordsBatchSize(100)
            .withPollTimeoutMs(10)
            .withErrorTimeoutMs(10)
            .withWaitingForLeaderTimeoutMs(10)
            .withShutdownHookEnabled(false)
            .build()

    private fun awaitOutboxCount(expectedCount: Int): Boolean {
        repeat(500) {
            if (jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") == expectedCount) {
                return true
            }
            Thread.sleep(10)
        }
        return false
    }
}
