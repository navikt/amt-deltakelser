package no.nav.amt.aktivitetskort.kafka.producer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.aktivitetskort.IntegrationTestBase
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.aktivitetskort.domain.AktivitetStatus
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
import no.nav.amt.lib.models.deltaker.Kilde
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
    @ParameterizedTest
    @ValueSource(strings = ["første kort", "samme kort", "ny periode"])
    fun `to samtidige publiseringer kan ikke lagre hvert sitt forslag fra samme historikk`(tilfelle: String) {
        val ctx = TestData.MockContext(deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET))
        testDatabase.insertArrangor(ctx.arrangor)
        testDatabase.insertDeltakerliste(ctx.deltakerliste)
        val periode = TestData.oppfolgingsperiode()
        if (tilfelle != "første kort") {
            val gammelPeriode = if (tilfelle == "samme kort") periode else TestData.oppfolgingsperiode()
            oppfolgingsperiodeRepository.upsert(gammelPeriode)
            meldingRepository.upsert(ctx.melding.copy(oppfolgingperiode = gammelPeriode.id))
        }
        val beggeForbereder = CyclicBarrier(2)
        val antallKall = AtomicInteger()
        every { amtDeltakerClient.getDeltaker(ctx.deltaker.id) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            TestData.lagDeltakerResponse(ctx.deltaker, ctx.deltakerliste, ctx.arrangor)
        }
        every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            if (antallKall.incrementAndGet() <= 2) beggeForbereder.await(10, TimeUnit.SECONDS)
            periode
        }

        Executors.newFixedThreadPool(2).use { executor ->
            val publiseringer = (1..2).map {
                executor.submit(Callable { runCatching { aktivitetskortService.lagAktivitetskort(ctx.deltaker.id) } })
            }
            val resultater = publiseringer.map { it.get(10, TimeUnit.SECONDS) }

            resultater.count { it.isSuccess } shouldBe 1
            resultater.count { it.exceptionOrNull() is ConcurrencyFailureException } shouldBe 1
        }

        val lagrede = meldingRepository.getByDeltakerId(ctx.deltaker.id)
        lagrede.size shouldBe if (tilfelle == "ny periode") 2 else 1
        val vinner = lagrede.single { it.oppfolgingperiode == periode.id }
        if (tilfelle == "samme kort") vinner.id shouldBe ctx.melding.id
        jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") shouldBe 1

        aktivitetskortService.lagAktivitetskort(ctx.deltaker.id).shouldNotBeNull().id shouldBe vinner.id
        meldingRepository.getByDeltakerId(ctx.deltaker.id).size shouldBe lagrede.size
        jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") shouldBe 2
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `sletting og resend velger kortet som ble lagret mens de ventet på låsen`(slett: Boolean) {
        val ctx = TestData.MockContext()
        testDatabase.insertArrangor(ctx.arrangor)
        testDatabase.insertDeltakerliste(ctx.deltakerliste)
        testDatabase.insertDeltaker(ctx.deltaker)
        meldingRepository.upsert(ctx.melding.copy(oppfolgingperiode = null))
        val nyId = UUID.randomUUID()
        val nyMelding = ctx.melding.copy(
            id = nyId,
            aktivitetskort = ctx.aktivitetskort.copy(id = nyId),
            oppfolgingperiode = null,
        )

        Executors.newSingleThreadExecutor().use { executor ->
            val ventende = transactionTemplate
                .execute {
                    meldingRepository.advisoryLockMedlemshistorikk(ctx.deltaker.id)
                    executor
                        .submit {
                            if (slett) {
                                aktivitetskortService.slettDeltaker(
                                    ctx.deltaker.id,
                                )
                            } else {
                                aktivitetskortService.resendSisteMelding(ctx.deltaker.id)
                            }
                        }.also {
                            ventPaAdvisoryLaas(jdbcTemplate)
                            meldingRepository.upsert(nyMelding)
                        }
                }.shouldNotBeNull()
            ventende.get(10, TimeUnit.SECONDS)
        }

        val key = jdbcTemplate.queryForObject("SELECT key FROM kafka_producer_record", ByteArray::class.java).shouldNotBeNull()
        key.toString(Charsets.UTF_8) shouldBe nyId.toString()
        meldingRepository.getByDeltakerId(ctx.deltaker.id).single { it.id == ctx.melding.id }.aktivitetskort shouldBe ctx.aktivitetskort
        if (slett) {
            deltakerRepository.get(ctx.deltaker.id) shouldBe null
            meldingRepository
                .getByDeltakerId(ctx.deltaker.id)
                .single { it.id == nyId }
                .aktivitetskort.aktivitetStatus shouldBe AktivitetStatus.AVBRUTT
        } else {
            deltakerRepository.get(ctx.deltaker.id) shouldBe ctx.deltaker
        }
    }

    @Test
    fun `overlappende grupper med ulik kortrekkefølge fullfører uten å vente på hverandre for alltid`() {
        val forste = TestData.MockContext(deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET))
        val deltakere = listOf(forste) + (1..2).map {
            TestData.MockContext(
                deltaker = TestData.lagDeltaker(deltakerlisteId = forste.deltakerliste.id, kilde = Kilde.KOMET),
                deltakerliste = forste.deltakerliste,
                arrangor = forste.arrangor,
            )
        }
        testDatabase.insertArrangor(forste.arrangor)
        testDatabase.insertDeltakerliste(forste.deltakerliste)
        deltakere.forEach { meldingRepository.upsert(it.melding.copy(oppfolgingperiode = null)) }
        every { veilarboppfolgingClient.hentOppfolgingperiode(any()) } returns TestData.oppfolgingsperiode()
        val forberedte = deltakere.map {
            aktivitetskortService.forberedMelding(
                Deltaker.fromDeltakerResponse(TestData.lagDeltakerResponse(it.deltaker, it.deltakerliste, it.arrangor)),
            )
        }
        val gruppe = AktivitetskortService.ForberedtGruppe(
            AktivitetskortService.Gruppeutvalg.Deltakerliste(forste.deltakerliste.id),
            meldingRepository.getByDeltakerlisteId(forste.deltakerliste.id),
            forberedte,
        )
        val grupper = listOf(gruppe, gruppe.copy(meldinger = forberedte.reversed()))
        val start = CyclicBarrier(2)

        Executors.newFixedThreadPool(2).use { executor ->
            val lagringer = grupper.map { gruppe ->
                executor.submit(
                    Callable {
                        start.await(10, TimeUnit.SECONDS)
                        runCatching { aktivitetskortService.lagreAktivitetskortGruppe(gruppe) }
                    },
                )
            }
            val resultater = lagringer.map { it.get(10, TimeUnit.SECONDS) }

            resultater.count { it.isSuccess } shouldBe 1
            resultater.count { it.exceptionOrNull() is ConcurrencyFailureException } shouldBe 1
        }

        jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") shouldBe 3
        deltakere.count { meldingRepository.getByDeltakerId(it.deltaker.id).single().oppfolgingperiode != null } shouldBe 3
    }

    @Test
    fun `en låst deltaker hindrer ikke lagring av kort for en annen deltaker`() {
        val ctx = TestData.MockContext()
        testDatabase.insertArrangor(ctx.arrangor)
        testDatabase.insertDeltakerliste(ctx.deltakerliste)

        Executors.newSingleThreadExecutor().use { executor ->
            transactionTemplate.executeWithoutResult {
                meldingRepository.advisoryLockMedlemshistorikk(UUID.randomUUID())
                executor.submit { meldingRepository.upsert(ctx.melding.copy(oppfolgingperiode = null)) }.get(10, TimeUnit.SECONDS)
            }
        }

        meldingRepository.getByDeltakerId(ctx.deltaker.id).single().id shouldBe ctx.melding.id
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `første kort venter til validert gruppeutvalg er ferdig eller angret`(angre: Boolean) {
        val ctx = TestData.MockContext()
        testDatabase.insertArrangor(ctx.arrangor)
        testDatabase.insertDeltakerliste(ctx.deltakerliste)
        val gruppe = aktivitetskortService.forberedAktivitetskort(ctx.deltakerliste.id)

        Executors.newSingleThreadExecutor().use { executor ->
            val skriving = transactionTemplate
                .execute { status ->
                    aktivitetskortService.lagreAktivitetskortGruppe(gruppe)
                    executor.submit { meldingRepository.upsert(ctx.melding.copy(oppfolgingperiode = null)) }.also {
                        ventPaAdvisoryLaas(jdbcTemplate)
                        it.isDone shouldBe false
                        meldingRepository.getByDeltakerId(ctx.deltaker.id) shouldBe emptyList()
                        if (angre) status.setRollbackOnly()
                    }
                }.shouldNotBeNull()
            skriving.get(10, TimeUnit.SECONDS)
        }

        meldingRepository.getByDeltakerId(ctx.deltaker.id).single().id shouldBe ctx.melding.id
    }

    @Test
    fun `første kort venter på arrangørlåsen`() {
        val ctx = TestData.MockContext()
        testDatabase.insertArrangor(ctx.arrangor)
        testDatabase.insertDeltakerliste(ctx.deltakerliste)
        val gruppe = aktivitetskortService.forberedAktivitetskortForArrangor(ctx.arrangor)

        Executors.newSingleThreadExecutor().use { executor ->
            transactionTemplate.executeWithoutResult {
                aktivitetskortService.lagreAktivitetskortGruppe(gruppe)
                executor
                    .submit {
                        meldingRepository.upsert(ctx.melding.copy(oppfolgingperiode = null))
                    }.also {
                        ventPaAdvisoryLaas(jdbcTemplate)
                        it.isDone shouldBe false
                    }
            }
        }

        meldingRepository.getByDeltakerId(ctx.deltaker.id).single().id shouldBe ctx.melding.id
    }

    @Test
    fun `første kort i en annen gruppe venter ikke på bulkoppdateringen`() {
        val ctx = TestData.MockContext()
        val annenDeltakerliste = TestData.lagDeltakerliste(arrangorId = ctx.arrangor.id)
        val annenGruppe = TestData.MockContext(
            deltaker = TestData.lagDeltaker(deltakerlisteId = annenDeltakerliste.id),
            deltakerliste = annenDeltakerliste,
            arrangor = ctx.arrangor,
        )
        testDatabase.insertArrangor(ctx.arrangor)
        testDatabase.insertDeltakerliste(ctx.deltakerliste)
        testDatabase.insertDeltakerliste(annenGruppe.deltakerliste)
        val gruppe = aktivitetskortService.forberedAktivitetskort(ctx.deltakerliste.id)

        Executors.newSingleThreadExecutor().use { executor ->
            transactionTemplate.executeWithoutResult {
                aktivitetskortService.lagreAktivitetskortGruppe(gruppe)
                executor
                    .submit {
                        meldingRepository.upsert(annenGruppe.melding.copy(oppfolgingperiode = null))
                    }.get(10, TimeUnit.SECONDS)
            }
        }

        meldingRepository.getByDeltakerId(annenGruppe.deltaker.id).single().id shouldBe annenGruppe.melding.id
    }

    @Test
    fun `gruppevalidering venter på pågående førstegangsskriving og oppdager nytt medlem`() {
        val ctx = TestData.MockContext()
        testDatabase.insertArrangor(ctx.arrangor)
        testDatabase.insertDeltakerliste(ctx.deltakerliste)
        val gruppe = aktivitetskortService.forberedAktivitetskort(ctx.deltakerliste.id)

        Executors.newSingleThreadExecutor().use { executor ->
            val lagring = transactionTemplate
                .execute {
                    aktivitetskortService.laasMeldingshistorikkForGruppe(
                        listOf(
                            AktivitetskortService.ForberedtMelding.Klar(
                                ctx.melding,
                                TestData.oppfolgingsperiode(),
                                AktivitetskortService.Meldingshistorikk(ctx.deltaker.id, emptyList()),
                            ),
                        ),
                    )
                    executor
                        .submit(Callable { runCatching { aktivitetskortService.lagreAktivitetskortGruppe(gruppe) } })
                        .also {
                            ventPaAdvisoryLaas(jdbcTemplate)
                            it.isDone shouldBe false
                            meldingRepository.upsert(ctx.melding.copy(oppfolgingperiode = null))
                        }
                }.shouldNotBeNull()
            (lagring.get(10, TimeUnit.SECONDS).exceptionOrNull() is ConcurrencyFailureException) shouldBe true
        }

        meldingRepository.getByDeltakerId(ctx.deltaker.id).single().id shouldBe ctx.melding.id
        jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") shouldBe 0
    }

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
                every { get(deltaker.id) } returns deltaker
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
                aktivitetskortService.slettDeltaker(deltaker.id)
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
