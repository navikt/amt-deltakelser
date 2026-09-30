package no.nav.amt.aktivitetskort.kafka.producer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.aktivitetskort.IntegrationTestBase
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.Melding
import no.nav.amt.aktivitetskort.kafka.KafkaOutboxLifecycle
import no.nav.amt.aktivitetskort.kafka.config.KafkaOutboxProcessorConfiguration
import no.nav.amt.aktivitetskort.kafka.consumer.AKTIVITETSKORT_TOPIC
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.MeldingRepository
import no.nav.amt.aktivitetskort.repositories.OppfolgingsperiodeRepository
import no.nav.amt.aktivitetskort.service.AktivitetskortService
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class KafkaOutboxTransactionTest(
    private val aktivitetskortProducer: AktivitetskortProducer,
    private val deltakerRepository: DeltakerRepository,
    private val meldingRepository: MeldingRepository,
    private val oppfolgingsperiodeRepository: OppfolgingsperiodeRepository,
    private val aktivitetskortService: AktivitetskortService,
    private val transactionTemplate: TransactionTemplate,
    private val jdbcTemplate: JdbcTemplate,
    private val producerRepository: PostgresJdbcTemplateProducerRepository,
) : IntegrationTestBase() {
    @Nested
    inner class LagreOgPubliser {
        @Test
        fun `melding og outbox-record rulles tilbake sammen`() {
            // Arrange
            val arrangor = TestData.lagArrangor()
            val deltakerliste = TestData.lagDeltakerliste(arrangorId = arrangor.id)
            testDatabase.insertDeltakerliste(deltakerliste)
            val deltakerDbo = TestData.lagDeltaker(deltakerlisteId = deltakerliste.id)
            val deltaker = Deltaker.fromDeltakerResponse(TestData.lagDeltakerResponse(deltakerDbo, deltakerliste, arrangor))
            val meldingId = UUID.randomUUID()
            val oppfolgingsperiode = TestData.oppfolgingsperiode()
            every { veilarboppfolgingClient.hentOppfolgingperiode(deltaker.personident) } returns oppfolgingsperiode
            jdbcTemplate.execute(
                "ALTER TABLE kafka_producer_record ADD CONSTRAINT test_outbox_insert_failure CHECK (false)",
            )

            // Act
            try {
                shouldThrow<DataIntegrityViolationException> {
                    aktivitetskortService.opprettMelding(deltaker, meldingId)
                }
            } finally {
                jdbcTemplate.execute("ALTER TABLE kafka_producer_record DROP CONSTRAINT test_outbox_insert_failure")
            }

            // Assert
            jdbcTemplate.queryForObject<Int>(
                "SELECT count(*) FROM melding WHERE id = ?",
                meldingId,
            ) shouldBe 0
            jdbcTemplate.queryForObject<Int>(
                "SELECT count(*) FROM oppfolgingsperiode WHERE id = ?",
                oppfolgingsperiode.id,
            ) shouldBe 0
            jdbcTemplate.queryForObject<Int>(
                "SELECT count(*) FROM kafka_producer_record",
            ) shouldBe 0
        }

        @Test
        fun `deltakersletting og aktivitetskortmelding rulles tilbake sammen`() {
            // Arrange
            val arrangor = TestData.lagArrangor()
            val deltakerliste = TestData.lagDeltakerliste(
                arrangorId = arrangor.id,
            )
            testDatabase.insertDeltakerliste(deltakerliste)
            val deltaker = TestData.lagDeltaker(
                deltakerlisteId = deltakerliste.id,
            )
            deltakerRepository.upsert(
                deltaker = deltaker,
                offset = 0,
            )
            val melding = TestData.melding(
                deltakerId = deltaker.id,
                deltakerlisteId = deltakerliste.id,
                arrangorId = arrangor.id,
            )
            meldingRepository.upsert(melding)
            val feilendeDeltakerRepository = mockk<DeltakerRepository> {
                every { delete(deltaker.id) } answers {
                    deltakerRepository.delete(deltaker.id)
                    throw IllegalStateException("Sletting feilet")
                }
            }
            val aktivitetskortService = AktivitetskortService(
                meldingRepository = meldingRepository,
                arrangorRepository = mockk<ArrangorRepository>(),
                aktivitetArenaAclClient = aktivitetArenaAclClient,
                amtArenaAclClient = amtArenaAclClient,
                unleashToggle = mockk<CommonUnleashToggle>(),
                veilarboppfolgingClient = veilarboppfolgingClient,
                oppfolgingsperiodeRepository = oppfolgingsperiodeRepository,
                deltakerRepository = feilendeDeltakerRepository,
                aktivitetskortProducer = aktivitetskortProducer,
                transactionTemplate = transactionTemplate,
                amtDeltakerClient = amtDeltakerClient,
                veilederUrlBasePath = TestData.VEILEDER_URL_BASEPATH,
                deltakerUrlBasePath = TestData.DELTAKER_URL_BASEPATH,
            )

            // Act
            shouldThrow<IllegalStateException> {
                aktivitetskortService.oppdaterAktivitetskortForSlettetDeltaker(deltaker, melding)
            }.message shouldBe "Sletting feilet"

            // Assert
            deltakerRepository.get(deltaker.id) shouldBe deltaker
            meldingRepository.getByDeltakerId(deltaker.id).single().aktivitetskort shouldBe melding.aktivitetskort
            jdbcTemplate.queryForObject<Int>(
                "SELECT count(*) FROM melding WHERE id = ?",
                melding.id,
            ) shouldBe 1
            jdbcTemplate.queryForObject<Int>(
                "SELECT count(*) FROM kafka_producer_record",
            ) shouldBe 0
        }

        @Test
        fun `melding lagres sammen med korrekt outbox-record`() {
            // Arrange
            val melding = lagMelding()
            val oppfolgingsperiode = TestData.oppfolgingsperiode()

            // Act
            transactionTemplate.executeWithoutResult {
                oppfolgingsperiodeRepository.upsert(oppfolgingsperiode)
                meldingRepository.upsert(melding)
                aktivitetskortProducer.send(melding.aktivitetskort)
            }

            // Assert
            jdbcTemplate.queryForObject<Int>(
                "SELECT count(*) FROM melding WHERE id = ?",
                melding.id,
            ) shouldBe 1
            val record = jdbcTemplate
                .query(
                    "SELECT topic, key, value FROM kafka_producer_record",
                ) { rs, _ ->
                    Triple(
                        rs.getString("topic"),
                        rs.getBytes("key")?.toString(Charsets.UTF_8),
                        rs.getBytes("value")?.toString(Charsets.UTF_8),
                    )
                }.single()

            record.first shouldBe AKTIVITETSKORT_TOPIC
            record.second shouldBe melding.aktivitetskort.id.toString()
            val value = record.third ?: error("Outbox-record mangler payload")
            val payload = objectMapper.readTree(value)
            payload.path("actionType").asString() shouldBe "UPSERT_AKTIVITETSKORT_V1"
            payload.path("aktivitetskort").path("id").asString() shouldBe melding.aktivitetskort.id.toString()
        }
    }

    @Nested
    inner class PubliserOutboxRecord {
        @Test
        fun `lagret outbox-record publiseres og slettes`() {
            // Arrange
            val melding = lagMelding()
            val oppfolgingsperiode = TestData.oppfolgingsperiode()
            transactionTemplate.executeWithoutResult {
                oppfolgingsperiodeRepository.upsert(oppfolgingsperiode)
                meldingRepository.upsert(melding)
                aktivitetskortProducer.send(melding.aktivitetskort)
            }
            val publishedRecord = AtomicReference<ProducerRecord<ByteArray, ByteArray>>()
            val kafkaProducer = mockk<Producer<ByteArray, ByteArray>>(relaxed = true)
            val kafkaProducerClient = mockk<KafkaProducerClient<ByteArray, ByteArray>>(relaxed = true) {
                every { getProducer() } returns kafkaProducer
                every { send(any(), any()) } answers {
                    publishedRecord.set(firstArg())
                    secondArg<Callback>().onCompletion(mockk<RecordMetadata>(), null)
                    CompletableFuture.completedFuture(mockk())
                }
            }
            val processor = KafkaOutboxProcessorConfiguration().kafkaProducerRecordProcessor(
                producerRepository = producerRepository,
                kafkaOutboxProducer = kafkaProducerClient,
                kafkaProducerLeaderElectionClient = LeaderElectionClient { true },
            )
            val lifecycle = KafkaOutboxLifecycle(processor)

            // Act
            try {
                lifecycle.start()
                awaitOutboxDrain()
            } finally {
                lifecycle.stop()
            }

            // Assert
            val record = publishedRecord.get().shouldNotBeNull()
            record.topic() shouldBe AKTIVITETSKORT_TOPIC
            record.key().toString(Charsets.UTF_8) shouldBe melding.aktivitetskort.id.toString()
            jdbcTemplate.queryForObject<Int>(
                "SELECT count(*) FROM kafka_producer_record",
            ) shouldBe 0
        }
    }

    @Nested
    inner class SlettAktivitetskort {
        @Test
        fun `kassering lagres med korrekt payload i outbox`() {
            // Arrange
            val aktivitetskortId = UUID.randomUUID()
            val personIdent = "12345678901"
            val navIdent = "Z123456"

            // Act
            transactionTemplate.executeWithoutResult {
                aktivitetskortProducer.slettAktivitetskort(
                    aktivitetskortId = aktivitetskortId,
                    personIdent = personIdent,
                    navIdent = navIdent,
                )
            }

            // Assert
            val record = jdbcTemplate
                .query(
                    "SELECT topic, key, value FROM kafka_producer_record",
                ) { rs, _ ->
                    Triple(
                        rs.getString("topic"),
                        rs.getBytes("key").toString(Charsets.UTF_8),
                        rs.getBytes("value").toString(Charsets.UTF_8),
                    )
                }.single()
            val payload = objectMapper.readTree(record.third)

            record.first shouldBe AKTIVITETSKORT_TOPIC
            record.second shouldBe aktivitetskortId.toString()
            payload.path("actionType").asString() shouldBe "KASSER_AKTIVITET"
            payload.path("aktivitetsId").asString() shouldBe aktivitetskortId.toString()
            payload.path("personIdent").asString() shouldBe personIdent
            payload.path("navIdent").asString() shouldBe navIdent
            payload.path("begrunnelse").asString() shouldBe "Kassering av duplikat aktivitetskort"
        }
    }

    @Nested
    inner class UtenTransaksjon {
        @Test
        fun `AktivitetskortProducer uten transaksjon - kaster og lagrer ingen outbox-record`() {
            // Act & Assert
            shouldThrow<IllegalTransactionStateException> {
                aktivitetskortProducer.send(lagMelding().aktivitetskort)
            }
            jdbcTemplate.queryForObject<Int>(
                "SELECT count(*) FROM kafka_producer_record",
            ) shouldBe 0
        }
    }

    private fun lagMelding(): Melding {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(
            arrangorId = arrangor.id,
        )
        testDatabase.insertDeltakerliste(deltakerliste)
        return TestData.melding(
            deltakerlisteId = deltakerliste.id,
            arrangorId = arrangor.id,
        )
    }

    private fun awaitOutboxDrain() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") shouldBe 0
        }
    }
}
