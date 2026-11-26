package no.nav.amt.aktivitetskort.kafka.consumer

import io.getunleash.FakeUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.aktivitetskort.IntegrationTestBase
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.aktivitetskort.database.TestData.toDto
import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.Aktivitetskort
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.domain.Tag
import no.nav.amt.aktivitetskort.domain.Tiltak
import no.nav.amt.aktivitetskort.exceptions.HistoriskArenaDeltakerException
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerlisteRepository
import no.nav.amt.aktivitetskort.repositories.MeldingRepository
import no.nav.amt.aktivitetskort.utils.shouldBeCloseTo
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.kafka.support.Acknowledgment
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KafkaConsumerTest(
    private val arrangorRepository: ArrangorRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val deltakerRepository: DeltakerRepository,
    private val meldingRepository: MeldingRepository,
    private val kafkaConsumer: KafkaConsumer,
    private val jdbcTemplate: JdbcTemplate,
    private val unleashClient: FakeUnleash,
    private val transactionTemplate: TransactionTemplate,
) : IntegrationTestBase() {
    private val offset: Long = 0
    private val ack: Acknowledgment = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        unleashClient.disableAll()
        every { aktivitetArenaAclClient.getAktivitetIdForArenaId(any()) } throws RuntimeException("Not configured")
    }

    @AfterEach
    fun restoreToggles() {
        unleashClient.enableAll()
    }

    @Test
    fun `listen - melding om ny arrangør - arrangør upsertes`() {
        val arrangor = TestData.lagArrangor()

        kafkaConsumer.listen(
            ConsumerRecord(ARRANGOR_TOPIC, 0, offset, arrangor.id.toString(), objectMapper.writeValueAsString(arrangor.toDto())),
            ack,
        )

        arrangorRepository.get(arrangor.id).shouldNotBeNull()
    }

    @Test
    fun `listen - melding om ny gjennomføring - deltakerliste lagres med riktige felter`() {
        val ctx = TestData.MockContext()
        arrangorRepository.upsert(ctx.arrangor)

        val payload = ctx.amtGjennomforingPayload

        kafkaConsumer.listen(
            ConsumerRecord(
                AMT_GJENNOMFORING_INTERN_TOPIC,
                0,
                offset,
                payload.id.toString(),
                objectMapper.writeValueAsString(payload),
            ),
            ack,
        )

        val gjennomforing = deltakerlisteRepository.get(payload.id)
        gjennomforing.shouldNotBeNull()
        assertSoftly(gjennomforing) {
            id shouldBe payload.id
            navn shouldBe payload.navn
            arrangorId shouldBe ctx.arrangor.id
            tiltak.navn shouldBe payload.tiltak.navn
            tiltak.tiltakskode shouldBe payload.tiltak.tiltakskode
        }
    }

    @Test
    fun `listen - melding om enkeltplass-gjennomføring - deltakerliste får tiltaksnavn som navn`() {
        val ctx = TestData.MockContext()
        arrangorRepository.upsert(ctx.arrangor)

        val payload = ctx.amtGjennomforingPayload.copy(
            type = GjennomforingType.Enkeltplass,
            navn = null,
        )

        kafkaConsumer.listen(
            ConsumerRecord(
                AMT_GJENNOMFORING_INTERN_TOPIC,
                0,
                offset,
                payload.id.toString(),
                objectMapper.writeValueAsString(payload),
            ),
            ack,
        )

        val lagret = deltakerlisteRepository.get(payload.id)
        lagret.shouldNotBeNull()
        lagret.navn shouldBe payload.tiltak.navn
    }

    @Test
    fun `listen - melding om ny deltaker - deltaker upsertes og aktivitetskort opprettes`() {
        val ctx = TestData.MockContext()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)

        mockAmtArenaAclClient(ctx.deltaker.id, 1234)
        mockAktivitetArenaAclClient(1234, ctx.melding.id)
        mockVeilarboppfolgingClient()
        mockAmtDeltakerClient(ctx)

        kafkaConsumer.listen(
            ConsumerRecord(
                DELTAKER_TOPIC,
                0,
                offset,
                ctx.deltaker.id.toString(),
                objectMapper.writeValueAsString(ctx.deltaker.toDto()),
            ),
            ack,
        )

        ctx.melding.id shouldBe ctx.aktivitetskortId
        ctx.melding.aktivitetskort.id shouldBe ctx.aktivitetskortId
        val deltaker = deltakerRepository.get(ctx.deltaker.id)

        deltaker.shouldNotBeNull()
        deltaker shouldBe ctx.deltaker

        val aktivitetskort = meldingRepository
            .getByDeltakerId(deltaker.id)
            .first()
            .aktivitetskort

        assertSoftly(aktivitetskort) {
            personident shouldBe ctx.aktivitetskort.personident
            tittel shouldBe ctx.aktivitetskort.tittel
            aktivitetStatus shouldBe ctx.aktivitetskort.aktivitetStatus
            startDato shouldBe ctx.aktivitetskort.startDato
            sluttDato shouldBe ctx.aktivitetskort.sluttDato
            beskrivelse shouldBe ctx.aktivitetskort.beskrivelse
            endretAv shouldBe ctx.aktivitetskort.endretAv
            endretTidspunkt shouldBeCloseTo ctx.aktivitetskort.endretTidspunkt
            avtaltMedNav shouldBe ctx.aktivitetskort.avtaltMedNav
            oppgave shouldBe ctx.aktivitetskort.oppgave
            handlinger shouldNotBe null
            detaljer shouldBe ctx.aktivitetskort.detaljer
            etiketter shouldBe ctx.aktivitetskort.etiketter
        }
    }

    @Test
    fun `listen - melding om oppdatert deltaker, dab sender ny id - nytt aktivitetskort opprettes med mottatt id`() {
        val ctx = TestData.MockContext(oppfolgingsperiodeId = null)
        val endretDeltaker = ctx.deltaker.copy(
            sluttdato = LocalDate.now().plusDays(1),
        )
        val nyId = UUID.randomUUID()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        deltakerRepository.upsert(ctx.deltaker, -1)
        meldingRepository.upsert(ctx.melding)

        mockAmtArenaAclClient(ctx.deltaker.id, 1234)
        mockAktivitetArenaAclClient(1234, nyId)
        mockVeilarboppfolgingClient()
        mockAmtDeltakerClient(ctx, endretDeltaker)

        kafkaConsumer.listen(
            ConsumerRecord(
                DELTAKER_TOPIC,
                0,
                offset,
                ctx.deltaker.id.toString(),
                objectMapper.writeValueAsString(endretDeltaker.toDto()),
            ),
            ack,
        )

        val deltaker = deltakerRepository.get(ctx.deltaker.id)
        deltaker.shouldNotBeNull()
        deltaker shouldBe endretDeltaker

        val aktivitetskort = meldingRepository
            .getByDeltakerId(deltaker.id)

        aktivitetskort.size shouldBe 2
        val nyttAktivitetskort = aktivitetskort
            .first()
            .aktivitetskort
        nyttAktivitetskort.id shouldBe nyId
        nyttAktivitetskort shouldBe ctx.aktivitetskort.copy(
            id = nyId,
            sluttDato = endretDeltaker.sluttdato,
        )
    }

    @Test
    fun `listen - tombstone for deltaker som har aktivt aktivitetskort - deltaker slettes og aktivitetskort avbrytes`() {
        val ctx = TestData.MockContext(oppfolgingsperiodeId = UUID.randomUUID())
        ctx.oppfolgingsperiodeId.shouldNotBeNull()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        deltakerRepository.upsert(ctx.deltaker, offset)
        meldingRepository.upsert(ctx.melding)

        kafkaConsumer.listen(
            ConsumerRecord(DELTAKER_TOPIC, 0, offset, ctx.deltaker.id.toString(), null),
            ack,
        )

        val aktivitetskort = meldingRepository
            .getByDeltakerId(ctx.deltaker.id)
            .first()
            .aktivitetskort
        aktivitetskort.aktivitetStatus shouldBe AktivitetStatus.AVBRUTT
        aktivitetskort.oppgave shouldBe null
        aktivitetskort.detaljer.first { it.label == Aktivitetskort.STATUS_FOR_DELTAKELSE }.verdi shouldBe "Avbrutt"
        aktivitetskort.etiketter.single().kode shouldBe Tag.Kode.AVBRUTT

        deltakerRepository.get(ctx.deltaker.id) shouldBe null
    }

    @Test
    fun `listen - tombstone for deltaker som har inaktivt aktivitetskort - deltaker slettes og aktivitetskort endres ikke`() {
        val ctx = TestData.MockContext(
            oppfolgingsperiodeId = UUID.randomUUID(),
            deltaker = TestData.lagDeltaker(status = DeltakerStatusModel(DeltakerStatus.Type.HAR_SLUTTET, null)),
        )
        ctx.oppfolgingsperiodeId.shouldNotBeNull()

        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        deltakerRepository.upsert(ctx.deltaker, offset)
        meldingRepository.upsert(ctx.melding)

        mockAmtArenaAclClient(ctx.deltaker.id, 1234)
        mockAktivitetArenaAclClient(1234, ctx.aktivitetskort.id)

        kafkaConsumer.listen(
            ConsumerRecord(DELTAKER_TOPIC, 0, offset, ctx.deltaker.id.toString(), null),
            ack,
        )

        val aktivitetskort = meldingRepository
            .getByDeltakerId(ctx.deltaker.id)
            .first()
            .aktivitetskort

        aktivitetskort.aktivitetStatus shouldBe AktivitetStatus.FULLFORT
        deltakerRepository.get(ctx.deltaker.id) shouldBe null
    }

    @Test
    fun `listen - tombstone for deltaker som ikke aktivitetskort - deltaker slettes og aktivitetskort opprettes ikke`() {
        val deltaker = TestData.lagDeltaker(status = DeltakerStatusModel(DeltakerStatus.Type.PABEGYNT_REGISTRERING, null))
        val deltakerliste = TestData.lagDeltakerliste(deltaker.deltakerlisteId)
        val arrangor = TestData.lagArrangor(deltakerliste.arrangorId)
        arrangorRepository.upsert(arrangor)
        deltakerlisteRepository.upsert(deltakerliste)
        deltakerRepository.upsert(deltaker, offset)

        kafkaConsumer.listen(
            ConsumerRecord(DELTAKER_TOPIC, 0, offset, deltaker.id.toString(), null),
            ack,
        )

        deltakerRepository.get(deltaker.id) shouldBe null
        meldingRepository.getByDeltakerId(deltaker.id) shouldBe emptyList()
    }

    @Test
    fun `teknisk API-feil lagrer ingen delvise endringer og samme deltakermelding kan leveres pa nytt`() {
        val ctx = TestData.MockContext()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        deltakerRepository.upsert(ctx.deltaker, 0)
        val endret = ctx.deltaker.copy(sluttdato = ctx.deltaker.sluttdato?.plusDays(1))
        val periodeId = UUID.randomUUID()
        mockAmtDeltakerClient(ctx, endret)
        mockVeilarboppfolgingClient(periodeId)
        mockAmtArenaAclClient(ctx.deltaker.id, 1234)
        val record = ConsumerRecord(
            DELTAKER_TOPIC,
            0,
            1,
            endret.id.toString(),
            objectMapper.writeValueAsString(endret.toDto()),
        )
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        shouldThrow<RuntimeException> { kafkaConsumer.listen(record, acknowledgment) }.message shouldBe "Not configured"

        deltakerRepository.get(endret.id) shouldBe ctx.deltaker
        jdbcTemplate.queryForObject<Long>("SELECT kafkaoffset FROM deltaker WHERE id = ?", endret.id) shouldBe 0
        meldingRepository.getByDeltakerId(endret.id) shouldBe emptyList()
        antallPerioder(periodeId) shouldBe 0
        antallOutbox(ctx.aktivitetskortId) shouldBe 0
        verify(exactly = 0) { acknowledgment.acknowledge() }

        mockAktivitetArenaAclClient(1234, ctx.aktivitetskortId)
        kafkaConsumer.listen(record, acknowledgment)

        deltakerRepository.get(endret.id) shouldBe endret
        jdbcTemplate.queryForObject<Long>("SELECT kafkaoffset FROM deltaker WHERE id = ?", endret.id) shouldBe 1
        meldingRepository.getByDeltakerId(endret.id).single().oppfolgingperiode shouldBe periodeId
        antallPerioder(periodeId) shouldBe 0
        antallOutbox(ctx.aktivitetskortId) shouldBe 1
        verify(exactly = 1) { acknowledgment.acknowledge() }

        every { veilarboppfolgingClient.hentOppfolgingperiode(any()) } throws IllegalStateException("Skal ikke kalles ved NoChange")
        kafkaConsumer.listen(record, acknowledgment)
        antallOutbox(ctx.aktivitetskortId) shouldBe 1
        verify(exactly = 2) { aktivitetArenaAclClient.getAktivitetIdForArenaId(1234) }
        verify(exactly = 2) { acknowledgment.acknowledge() }
    }

    @Test
    fun `manglende oppfolgingsperiode lagrer deltaker uten kort`() {
        val ctx = TestData.MockContext()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        mockAmtDeltakerClient(ctx)
        every { veilarboppfolgingClient.hentOppfolgingperiode(any()) } returns null
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        kafkaConsumer.listen(deltakerRecord(ctx), acknowledgment)

        deltakerRepository.get(ctx.deltaker.id) shouldBe ctx.deltaker
        meldingRepository.getByDeltakerId(ctx.deltaker.id) shouldBe emptyList()
        antallOutbox(ctx.aktivitetskortId) shouldBe 0
        verify(exactly = 0) { amtArenaAclClient.getArenaIdForAmtId(any()) }
        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `historisk Arena-deltaker lagres uten kort eller oppfolgingsperiode`() {
        val ctx = TestData.MockContext()
        val periodeId = UUID.randomUUID()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        mockAmtDeltakerClient(ctx)
        mockVeilarboppfolgingClient(periodeId)
        every { amtArenaAclClient.getArenaIdForAmtId(ctx.deltaker.id) } throws HistoriskArenaDeltakerException("Historisk deltaker")

        kafkaConsumer.listen(deltakerRecord(ctx), ack)

        deltakerRepository.get(ctx.deltaker.id) shouldBe ctx.deltaker
        meldingRepository.getByDeltakerId(ctx.deltaker.id) shouldBe emptyList()
        antallPerioder(periodeId) shouldBe 0
        antallOutbox(ctx.aktivitetskortId) shouldBe 0
        verify(exactly = 0) { aktivitetArenaAclClient.getAktivitetIdForArenaId(any()) }
    }

    @Test
    fun `avsluttet Arena-deltaker fra for oppfolgingsperioden lagres uten periodekopi eller kort`() {
        val ctx = TestData.MockContext(
            deltaker = TestData.lagDeltaker(
                status = DeltakerStatusModel(DeltakerStatus.Type.FULLFORT, null, LocalDate.now().minusMonths(2).atStartOfDay()),
            ),
        )
        val periodeId = UUID.randomUUID()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        mockAmtDeltakerClient(ctx)
        mockVeilarboppfolgingClient(periodeId)

        kafkaConsumer.listen(deltakerRecord(ctx), ack)

        deltakerRepository.get(ctx.deltaker.id) shouldBe ctx.deltaker
        meldingRepository.getByDeltakerId(ctx.deltaker.id) shouldBe emptyList()
        antallPerioder(periodeId) shouldBe 0
        antallOutbox(ctx.aktivitetskortId) shouldBe 0
        verify(exactly = 0) { amtArenaAclClient.getArenaIdForAmtId(any()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `delvis feil i gruppe ruller tilbake alle kort og samme melding kan leveres pa nytt`(arrangorHendelse: Boolean) {
        val forste = TestData.MockContext(deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET))
        val andreArrangor = if (arrangorHendelse) {
            TestData.lagArrangor(overordnetArrangorId = forste.arrangor.id)
        } else {
            forste.arrangor
        }
        val andreListe = if (arrangorHendelse) {
            TestData.lagDeltakerliste(arrangorId = andreArrangor.id)
        } else {
            forste.deltakerliste
        }
        val andre = TestData.MockContext(
            deltaker = TestData.lagDeltaker(deltakerlisteId = andreListe.id, kilde = Kilde.KOMET),
            deltakerliste = andreListe,
            arrangor = andreArrangor,
        )
        val deltakere = listOf(forste, andre)
        deltakere.forEach {
            arrangorRepository.upsert(it.arrangor)
            deltakerlisteRepository.upsert(it.deltakerliste)
            deltakerRepository.upsert(it.deltaker, 0)
            meldingRepository.upsert(it.melding)
        }
        val periodeId = UUID.randomUUID()
        mockVeilarboppfolgingClient(periodeId)
        var antallOppslag = 0
        var skalFeile = true
        every { amtDeltakerClient.getDeltaker(any()) } answers {
            antallOppslag++
            if (skalFeile && antallOppslag == 2) {
                deltakere.sumOf { antallOutbox(it.aktivitetskortId) } shouldBe 1
                throw IllegalStateException("Andre deltaker feiler")
            }
            val ctx = deltakere.single { it.deltaker.id == firstArg<UUID>() }
            TestData.lagDeltakerResponse(
                ctx.deltaker.copy(sluttdato = LocalDate.now().plusMonths(2)),
                ctx.deltakerliste,
                ctx.arrangor,
            )
        }
        val endretArrangor = forste.arrangor.copy(navn = "Endret Navn")
        val endretListe = forste.deltakerliste.copy(navn = "Endret navn")
        val record = if (arrangorHendelse) {
            ConsumerRecord(ARRANGOR_TOPIC, 0, 1, endretArrangor.id.toString(), objectMapper.writeValueAsString(endretArrangor.toDto()))
        } else {
            ConsumerRecord(
                AMT_GJENNOMFORING_INTERN_TOPIC,
                0,
                1,
                endretListe.id.toString(),
                objectMapper.writeValueAsString(
                    TestData.lagAmtGjennomforingPayload(arrangor = forste.arrangor, deltakerliste = endretListe),
                ),
            )
        }
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        shouldThrow<IllegalStateException> { kafkaConsumer.listen(record, acknowledgment) }

        arrangorRepository.get(forste.arrangor.id) shouldBe forste.arrangor
        deltakerlisteRepository.get(forste.deltakerliste.id) shouldBe forste.deltakerliste
        deltakere.forEach {
            meldingRepository.getByDeltakerId(it.deltaker.id).single().aktivitetskort shouldBe it.aktivitetskort
            antallOutbox(it.aktivitetskortId) shouldBe 0
        }
        antallPerioder(periodeId) shouldBe 0
        verify(exactly = 0) { acknowledgment.acknowledge() }

        skalFeile = false
        kafkaConsumer.listen(record, acknowledgment)

        if (arrangorHendelse) {
            arrangorRepository.get(forste.arrangor.id) shouldBe endretArrangor
        } else {
            deltakerlisteRepository.get(forste.deltakerliste.id) shouldBe endretListe
        }
        deltakere.forEach {
            meldingRepository
                .getByDeltakerId(it.deltaker.id)
                .single()
                .aktivitetskort.sluttDato shouldBe LocalDate.now().plusMonths(2)
            antallOutbox(it.aktivitetskortId) shouldBe 1
        }
        antallOppslag shouldBe 4
        kafkaConsumer.listen(record, acknowledgment)
        antallOppslag shouldBe 4
        deltakere.forEach { antallOutbox(it.aktivitetskortId) shouldBe 1 }
        verify(exactly = 2) { acknowledgment.acknowledge() }
    }

    @ParameterizedTest
    @CsvSource("ARENA,false", "ARENA,true", "KOMET,false", "KOMET,true")
    fun `alle HTTP-kall for deltaker skjer uten transaksjon og kortet lagres som for`(
        kilde: Kilde,
        eksisterende: Boolean,
    ) {
        val periode = TestData.oppfolgingsperiode()
        val ctx = TestData.MockContext(
            deltaker = TestData.lagDeltaker(kilde = kilde),
            oppfolgingsperiodeId = periode.id,
        )
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        if (eksisterende) {
            deltakerRepository.upsert(ctx.deltaker.copy(sluttdato = LocalDate.now()), -1)
            meldingRepository.upsert(ctx.melding)
        }
        every { amtDeltakerClient.getDeltaker(ctx.deltaker.id) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            TestData.lagDeltakerResponse(ctx.deltaker, ctx.deltakerliste, ctx.arrangor)
        }
        every { veilarboppfolgingClient.hentOppfolgingperiode(any()) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            periode
        }
        every { amtArenaAclClient.getArenaIdForAmtId(ctx.deltaker.id) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            1234L
        }
        every { aktivitetArenaAclClient.getAktivitetIdForArenaId(1234) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            ctx.aktivitetskortId
        }

        kafkaConsumer.listen(deltakerRecord(ctx), ack)

        val melding = meldingRepository.getByDeltakerId(ctx.deltaker.id).single()
        deltakerRepository.get(ctx.deltaker.id) shouldBe ctx.deltaker
        melding.aktivitetskort shouldBe ctx.aktivitetskort.copy(id = melding.id)
        melding.oppfolgingperiode shouldBe periode.id
        antallOutbox(melding.id) shouldBe 1
        if (kilde == Kilde.ARENA || eksisterende) {
            melding.id shouldBe ctx.aktivitetskortId
        }
        verify(exactly = if (kilde == Kilde.ARENA) 1 else 0) { amtArenaAclClient.getArenaIdForAmtId(any()) }
        verify(exactly = if (kilde == Kilde.ARENA) 1 else 0) { aktivitetArenaAclClient.getAktivitetIdForArenaId(any()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `nytt offset under forberedelse forkaster ogsa periode fra avvist kort`(avsluttet: Boolean) {
        val ctx = TestData.MockContext(
            deltaker = TestData.lagDeltaker(
                status = DeltakerStatusModel(
                    if (avsluttet) DeltakerStatus.Type.FULLFORT else DeltakerStatus.Type.DELTAR,
                    null,
                    LocalDate.now().minusMonths(2).atStartOfDay(),
                ),
            ),
        )
        val periode = TestData.oppfolgingsperiode()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        mockAmtDeltakerClient(ctx)
        mockAmtArenaAclClient(ctx.deltaker.id, 1234)
        mockAktivitetArenaAclClient(1234, ctx.aktivitetskortId)
        every { veilarboppfolgingClient.hentOppfolgingperiode(any()) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            deltakerRepository.upsert(ctx.deltaker, offset + 1)
            periode
        }

        kafkaConsumer.listen(deltakerRecord(ctx), ack)

        jdbcTemplate.queryForObject<Long>("SELECT kafkaoffset FROM deltaker WHERE id = ?", ctx.deltaker.id) shouldBe offset + 1
        meldingRepository.getByDeltakerId(ctx.deltaker.id) shouldBe emptyList()
        antallPerioder(periode.id) shouldBe 0
        antallOutbox(ctx.aktivitetskortId) shouldBe 0
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `uendret deltaker oppdateres fortsatt ved toggle eller bypass for opplaering`(opplaering: Boolean) {
        if (!opplaering) unleashClient.enableAll()
        val liste = TestData.lagDeltakerliste(
            tiltak = Tiltak(
                "Tiltak",
                if (opplaering) Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING else Tiltakskode.OPPFOLGING,
            ),
        )
        val ctx = TestData.MockContext(
            deltaker = TestData.lagDeltaker(deltakerlisteId = liste.id, kilde = Kilde.KOMET),
            deltakerliste = liste,
        )
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        deltakerRepository.upsert(ctx.deltaker, offset)
        meldingRepository.upsert(ctx.melding)
        mockAmtDeltakerClient(ctx)
        mockVeilarboppfolgingClient()
        val record = deltakerRecord(ctx)

        kafkaConsumer.listen(record, ack)
        kafkaConsumer.listen(record, ack)

        antallOutbox(ctx.aktivitetskortId) shouldBe 2
        meldingRepository.getByDeltakerId(ctx.deltaker.id).single().id shouldBe ctx.aktivitetskortId
        verify(exactly = 2) { veilarboppfolgingClient.hentOppfolgingperiode(any()) }
    }

    @Test
    fun `databasefeil etter deltaker-upsert ruller tilbake alt og tillater gjenlevering`() {
        val ctx = TestData.MockContext()
        val periodeId = UUID.randomUUID()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        mockVeilarboppfolgingClient(periodeId)
        mockAmtArenaAclClient(ctx.deltaker.id, 1234)
        mockAktivitetArenaAclClient(1234, ctx.aktivitetskortId)
        every { amtDeltakerClient.getDeltaker(ctx.deltaker.id) } returns TestData.lagDeltakerResponse(
            ctx.deltaker,
            ctx.deltakerliste.copy(id = UUID.randomUUID()),
            ctx.arrangor,
        )
        val record = deltakerRecord(ctx)
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        shouldThrow<DataIntegrityViolationException> { kafkaConsumer.listen(record, acknowledgment) }

        deltakerRepository.get(ctx.deltaker.id) shouldBe null
        meldingRepository.getByDeltakerId(ctx.deltaker.id) shouldBe emptyList()
        antallPerioder(periodeId) shouldBe 0
        antallOutbox(ctx.aktivitetskortId) shouldBe 0
        verify(exactly = 0) { acknowledgment.acknowledge() }

        mockAmtDeltakerClient(ctx)
        kafkaConsumer.listen(record, acknowledgment)

        deltakerRepository.get(ctx.deltaker.id) shouldBe ctx.deltaker
        meldingRepository.getByDeltakerId(ctx.deltaker.id).single().id shouldBe ctx.aktivitetskortId
        antallPerioder(periodeId) shouldBe 0
        antallOutbox(ctx.aktivitetskortId) shouldBe 1
        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @ParameterizedTest
    @CsvSource("false,false,false", "false,true,false", "true,false,false", "true,true,false", "false,false,true", "false,true,true")
    fun `overlappende leveringer ruller tilbake utdatert kort og gjenlevering gjenbruker ID`(
        tidligerePeriode: Boolean,
        sammeOffset: Boolean,
        sammePeriode: Boolean,
    ) {
        val periode = TestData.oppfolgingsperiode()
        val ctx = TestData.MockContext(deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET))
        val gammelDeltaker = ctx.deltaker.copy(sluttdato = LocalDate.now())
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        if (tidligerePeriode || sammePeriode) {
            deltakerRepository.upsert(gammelDeltaker, -1)
            val gammelPeriodeId = if (sammePeriode) periode.id else UUID.randomUUID()
            meldingRepository.upsert(ctx.melding.copy(oppfolgingperiode = gammelPeriodeId))
        }
        every { amtDeltakerClient.getDeltaker(ctx.deltaker.id) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            TestData.lagDeltakerResponse(ctx.deltaker, ctx.deltakerliste, ctx.arrangor)
        }
        every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            periode
        }
        val records = (1L..2L).map {
            ConsumerRecord(
                DELTAKER_TOPIC,
                0,
                if (sammeOffset) 1L else it,
                ctx.deltaker.id.toString(),
                objectMapper.writeValueAsString(ctx.deltaker.toDto()),
            )
        }
        val acknowledgments = records.map { mockk<Acknowledgment>(relaxed = true) }

        Executors.newFixedThreadPool(2).use { executor ->
            val leveringer = transactionTemplate
                .execute {
                    jdbcTemplate.execute("LOCK TABLE deltaker IN SHARE MODE")
                    records
                        .mapIndexed { index, record ->
                            executor.submit(
                                Callable { runCatching { kafkaConsumer.listen(record, acknowledgments[index]) } },
                            )
                        }.also {
                            await().atMost(10, TimeUnit.SECONDS).untilAsserted {
                                jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()")
                                jdbcTemplate.queryForObject<Int>(
                                    """
                                    SELECT count(*) FROM pg_stat_activity
                                    WHERE wait_event_type = 'Lock' AND query LIKE '%insert into deltaker(%'
                                    """.trimIndent(),
                                ) shouldBe 2
                            }
                        }
                }.shouldNotBeNull()
            val resultater = leveringer.map { it.get(10, TimeUnit.SECONDS) }
            meldingRepository.getByDeltakerId(ctx.deltaker.id).count { it.oppfolgingperiode == periode.id } shouldBe 1
            resultater.count { it.isSuccess } shouldBe 1
            val vinner = resultater.indexOfFirst { it.isSuccess }
            val taper = 1 - vinner
            (resultater[taper].exceptionOrNull() is ConcurrencyFailureException) shouldBe true
            jdbcTemplate.queryForObject<Long>(
                "SELECT kafkaoffset FROM deltaker WHERE id = ?",
                ctx.deltaker.id,
            ) shouldBe records[vinner].offset()
            verify(exactly = 1) { acknowledgments[vinner].acknowledge() }
            verify(exactly = 0) { acknowledgments[taper].acknowledge() }
            val melding = meldingRepository.getByDeltakerId(ctx.deltaker.id).single { it.oppfolgingperiode == periode.id }
            if (sammePeriode) melding.id shouldBe ctx.aktivitetskortId
            antallOutbox(melding.id) shouldBe 1
            antallPerioder(periode.id) shouldBe 0

            unleashClient.enableAll()
            kafkaConsumer.listen(records.last(), ack)
            if (!sammeOffset) kafkaConsumer.listen(records.first(), ack)

            val meldinger = meldingRepository.getByDeltakerId(ctx.deltaker.id)
            meldinger.size shouldBe if (tidligerePeriode) 2 else 1
            meldinger.single { it.oppfolgingperiode == periode.id }.id shouldBe melding.id
            antallOutbox(melding.id) shouldBe 2
            deltakerRepository.get(ctx.deltaker.id) shouldBe ctx.deltaker
        }
    }

    private fun deltakerRecord(ctx: TestData.MockContext) = ConsumerRecord(
        DELTAKER_TOPIC,
        0,
        offset,
        ctx.deltaker.id.toString(),
        objectMapper.writeValueAsString(ctx.deltaker.toDto()),
    )

    private fun antallPerioder(id: UUID): Int = jdbcTemplate
        .queryForObject<Int>(
            "SELECT count(*) FROM oppfolgingsperiode WHERE id = ?",
            id,
        ).shouldNotBeNull()

    private fun antallOutbox(id: UUID): Int = jdbcTemplate
        .queryForObject<Int>(
            "SELECT count(*) FROM kafka_producer_record WHERE key = ?",
            id.toString().toByteArray(),
        ).shouldNotBeNull()

    private fun mockAmtArenaAclClient(
        amtId: UUID,
        arenaId: Long,
    ) {
        every { amtArenaAclClient.getArenaIdForAmtId(amtId) } returns arenaId
    }

    private fun mockAktivitetArenaAclClient(
        arenaId: Long,
        aktivitetId: UUID,
    ) {
        every { aktivitetArenaAclClient.getAktivitetIdForArenaId(arenaId) } returns aktivitetId
    }

    private fun mockVeilarboppfolgingClient(oppfolgingsperiodeId: UUID = UUID.randomUUID()) {
        every { veilarboppfolgingClient.hentOppfolgingperiode(any()) } returns Oppfolgingsperiode(
            id = oppfolgingsperiodeId,
            startDato = LocalDateTime.now().minusDays(5),
            sluttDato = null,
        )
    }

    private fun mockAmtDeltakerClient(
        ctx: TestData.MockContext,
        deltaker: no.nav.amt.aktivitetskort.domain.DeltakerDbo = ctx.deltaker,
    ) {
        every { amtDeltakerClient.getDeltaker(deltaker.id) } returns TestData.lagDeltakerResponse(
            deltaker = deltaker,
            deltakerliste = ctx.deltakerliste,
            arrangor = ctx.arrangor,
        )
    }
}
