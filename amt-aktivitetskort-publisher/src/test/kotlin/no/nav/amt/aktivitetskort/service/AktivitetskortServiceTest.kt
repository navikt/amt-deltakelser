package no.nav.amt.aktivitetskort.service

import io.getunleash.DefaultUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.amt.aktivitetskort.client.AktivitetArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtDeltakerClient
import no.nav.amt.aktivitetskort.client.VeilarboppfolgingClient
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.DeltakerDbo
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.domain.Detalj
import no.nav.amt.aktivitetskort.domain.Melding
import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.domain.Tiltak
import no.nav.amt.aktivitetskort.exceptions.HistoriskArenaDeltakerException
import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import no.nav.amt.aktivitetskort.mock.mockCluster
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerlisteRepository
import no.nav.amt.aktivitetskort.repositories.MeldingRepository
import no.nav.amt.aktivitetskort.repositories.OppfolgingsperiodeRepository
import no.nav.amt.aktivitetskort.utils.shouldBeCloseTo
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.function.Consumer

@TestPropertySource(properties = ["NAIS_CLUSTER_NAME=dev-gcp"])
class AktivitetskortServiceTest {
    private val meldingRepository = mockk<MeldingRepository>(relaxUnitFun = true)
    private val arrangorRepository = mockk<ArrangorRepository>()
    private val deltakerlisteRepository = mockk<DeltakerlisteRepository>()
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val aktivitetArenaAclClient = mockk<AktivitetArenaAclClient>()
    private val amtArenaAclClient = mockk<AmtArenaAclClient>()
    private val amtDeltakerClient = mockk<AmtDeltakerClient>()
    private val unleash = mockk<DefaultUnleash>()
    private val veilarboppfolgingClient = mockk<VeilarboppfolgingClient>()
    private val oppfolgingsperiodeRepository = mockk<OppfolgingsperiodeRepository>()
    private val aktivitetskortProducer = mockk<AktivitetskortProducer>(relaxed = true)
    private val transactionTemplate = mockk<TransactionTemplate>()

    private val aktivitetskortService = AktivitetskortService(
        meldingRepository = meldingRepository,
        arrangorRepository = arrangorRepository,
        aktivitetArenaAclClient = aktivitetArenaAclClient,
        amtArenaAclClient = amtArenaAclClient,
        unleashToggle = CommonUnleashToggle(unleash),
        veilarboppfolgingClient = veilarboppfolgingClient,
        oppfolgingsperiodeRepository = oppfolgingsperiodeRepository,
        deltakerRepository = deltakerRepository,
        aktivitetskortProducer = aktivitetskortProducer,
        transactionTemplate = transactionTemplate,
        amtDeltakerClient = amtDeltakerClient,
        veilederUrlBasePath = TestData.VEILEDER_URL_BASEPATH,
        deltakerUrlBasePath = TestData.DELTAKER_URL_BASEPATH,
    )
    private val nyPeriode = Oppfolgingsperiode(
        UUID.randomUUID(),
        LocalDateTime.now().minusDays(5),
        null,
    )

    @BeforeEach
    fun setup() {
        every { oppfolgingsperiodeRepository.upsert(any()) } returns TestData.oppfolgingsperiode()
        every { transactionTemplate.executeWithoutResult(any<Consumer<TransactionStatus>>()) } answers {
            firstArg<Consumer<TransactionStatus>>().accept(SimpleTransactionStatus())
        }
    }

    private fun TestData.MockContext.deltakerModel(deltaker: DeltakerDbo = this.deltaker): Deltaker = Deltaker.fromDeltakerResponse(
        TestData.lagDeltakerResponse(
            deltaker = deltaker,
            deltakerliste = this.deltakerliste,
            arrangor = this.arrangor,
        ),
    )

    private fun TestData.MockContext.stubDeltakerFraAmtDeltaker(deltaker: DeltakerDbo = this.deltaker): Deltaker =
        deltakerModel(deltaker).also {
            every { amtDeltakerClient.getDeltaker(deltaker.id) } returns TestData.lagDeltakerResponse(
                deltaker = deltaker,
                deltakerliste = this.deltakerliste,
                arrangor = this.arrangor,
            )
        }

    @Nested
    inner class OppdaterAktivitetskortForSlettetDeltaker {
        @Test
        fun `beholder datoer og avtalt med Nav fra eksisterende aktivitetskort`() {
            // Arrange
            val deltaker = TestData.lagDeltaker(
                status = DeltakerStatusModel(DeltakerStatus.Type.UTKAST_TIL_PAMELDING, null),
                oppstartsdato = LocalDate.now().plusDays(10),
                sluttdato = LocalDate.now().plusDays(20),
            )
            val aktivitetskort = TestData.aktivitetskort(
                personIdent = "tidligere-personident",
                startDato = LocalDate.now().minusDays(10),
                sluttDato = LocalDate.now().minusDays(1),
                avtaltMedNav = false,
                detaljer = listOf(Detalj("Status for deltakelse", "Utkast til påmelding")),
            )
            val melding = TestData.melding(
                deltakerId = deltaker.id,
                aktivitetskort = aktivitetskort,
            )
            val lagretMelding = slot<Melding>()
            every { meldingRepository.upsert(capture(lagretMelding)) } returns Unit
            every { deltakerRepository.delete(deltaker.id) } returns Unit

            // Act
            aktivitetskortService.oppdaterAktivitetskortForSlettetDeltaker(deltaker, melding)

            // Assert
            lagretMelding.captured.aktivitetskort.startDato shouldBe aktivitetskort.startDato
            lagretMelding.captured.aktivitetskort.sluttDato shouldBe aktivitetskort.sluttDato
            lagretMelding.captured.aktivitetskort.avtaltMedNav shouldBe aktivitetskort.avtaltMedNav
            lagretMelding.captured.aktivitetskort.aktivitetStatus shouldBe AktivitetStatus.AVBRUTT
        }
    }

    @Nested
    inner class LagAktivitetskort {
        @Test
        fun `lagAktivitetskort - kilde=ARENA - lager nytt aktivitetskort`() {
            // Arrange
            val ctx = TestData.MockContext()
            val aktivitetskordId = UUID.randomUUID()
            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { amtArenaAclClient.getArenaIdForAmtId(ctx.deltaker.id) } returns 1L
            every { aktivitetArenaAclClient.getAktivitetIdForArenaId(1L) } returns aktivitetskordId
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode

            // Act
            val aktivitetskort = aktivitetskortService.lagAktivitetskort(ctx.deltakerModel())

            // Assert
            verify(exactly = 1) { meldingRepository.upsert(any()) }

            assertSoftly(aktivitetskort.shouldNotBeNull()) {
                id shouldBe aktivitetskordId
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
                handlinger shouldBe ctx.aktivitetskort.handlinger
                detaljer shouldBe ctx.aktivitetskort.detaljer
                etiketter shouldBe ctx.aktivitetskort.etiketter
            }
        }

        @Test
        fun `lagAktivitetskort - henter deltaker fra amt-deltaker`() = mockCluster {
            // Arrange
            val deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET)
            val ctx = TestData.MockContext(deltaker = deltaker)

            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode

            // Act
            val aktivitetskort = aktivitetskortService.lagAktivitetskort(ctx.deltakerModel())

            // Assert
            aktivitetskort shouldNotBe null
        }

        @Test
        fun `lagAktivitetskort - kilde=KOMET - lager nytt aktivitetskort med lenker`() {
            // Arrange
            val deltakerliste = TestData.lagDeltakerliste(
                tiltak = Tiltak("Arbeidsforberedende trening", Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
            )
            val deltaker =
                TestData.lagDeltaker(kilde = Kilde.KOMET, deltakerlisteId = deltakerliste.id, prosentStilling = null, dagerPerUke = null)
            val ctx = TestData.MockContext(deltaker = deltaker, deltakerliste = deltakerliste)

            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode

            // Act
            val aktivitetskort = aktivitetskortService.lagAktivitetskort(ctx.deltakerModel())

            // Assert
            verify(exactly = 1) { meldingRepository.upsert(any()) }

            assertSoftly(aktivitetskort.shouldNotBeNull()) {
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
                handlinger shouldBe ctx.aktivitetskort.handlinger
                detaljer shouldBe ctx.aktivitetskort.detaljer
                etiketter shouldBe ctx.aktivitetskort.etiketter
            }
        }

        @Test
        fun `lagAktivitetskort - kilde=ARENA, kall til amt-arena-acl feiler - oppretting feiler`() {
            // Arrange
            val ctx = TestData.MockContext()
            val aktivitetskordId = UUID.randomUUID()
            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { amtArenaAclClient.getArenaIdForAmtId(ctx.deltaker.id) } throws IllegalStateException("Noe gikk galt")
            every { aktivitetArenaAclClient.getAktivitetIdForArenaId(any()) } returns aktivitetskordId
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode

            // Act
            assertThrows<IllegalStateException> {
                aktivitetskortService.lagAktivitetskort(ctx.deltakerModel())
            }

            // Assert
            verify(exactly = 0) { meldingRepository.upsert(any()) }
            verify(exactly = 0) { aktivitetArenaAclClient.getAktivitetIdForArenaId(any()) }
        }

        @Test
        fun `lagAktivitetskort - kilde=KOMET, arenaId finnes ikke - oppretter med ny aktivitetskortId`() = mockCluster {
            // Arrange
            val deltakerliste = TestData.lagDeltakerliste(
                tiltak = Tiltak("Arbeidsforberedende trening", Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
            )
            val deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET, deltakerlisteId = deltakerliste.id)
            val ctx = TestData.MockContext(deltaker = deltaker, deltakerliste = deltakerliste)

            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { amtArenaAclClient.getArenaIdForAmtId(ctx.deltaker.id) } returns null
            every { unleash.isEnabled(any()) } returns true
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode

            // Act
            aktivitetskortService.lagAktivitetskort(ctx.deltakerModel())

            // Assert
            verify(exactly = 1) { meldingRepository.upsert(any()) }
            verify(exactly = 0) { aktivitetArenaAclClient.getAktivitetIdForArenaId(any()) }
        }

        @Test
        fun `lagAktivitetskort - oppdatering på hist deltaker - oppretter ikke aktivitetskort`() {
            // Arrange
            val ctx = TestData.MockContext()
            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { amtArenaAclClient.getArenaIdForAmtId(ctx.deltaker.id) } throws HistoriskArenaDeltakerException("Noe gikk galt")
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode

            // Act
            val aktivitetskort = aktivitetskortService.lagAktivitetskort(ctx.deltakerModel())

            // Assert
            aktivitetskort shouldBe null
        }

        @Test
        fun `lagAktivitetskort - kilde=ARENA klarer ikke hente id - oppretting feiler`() {
            // Arrange
            val ctx = TestData.MockContext()
            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { amtArenaAclClient.getArenaIdForAmtId(ctx.deltaker.id) } returns 1L
            every { aktivitetArenaAclClient.getAktivitetIdForArenaId(1L) } throws IllegalStateException("Noe gikk galt")
            every { unleash.isEnabled(any()) } returns false
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode

            // Act
            assertThrows<IllegalStateException> {
                aktivitetskortService.lagAktivitetskort(ctx.deltakerModel())
            }

            // Assert
            verify(exactly = 0) { meldingRepository.upsert(any()) }
            verify(exactly = 1) { aktivitetArenaAclClient.getAktivitetIdForArenaId(any()) }
        }

        @Test
        fun `lagAktivitetskort - tidligere meldinger uten oppfølgingsperiode - oppdaterer eksisterende aktivitetskort`() {
            // Arrange
            val ctx = TestData.MockContext(oppfolgingsperiodeId = null, deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET))
            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns listOf(ctx.melding)
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode

            // Act
            val aktivitetskort = aktivitetskortService.lagAktivitetskort(ctx.deltakerModel())

            // Assert
            verify(exactly = 1) { meldingRepository.upsert(any()) }
            aktivitetskort shouldBe ctx.aktivitetskort
        }

        @Test
        fun `lagAktivitetskort - meldinger finnes med en annen oppfølgingsperiode - lager nytt aktivitetskort`() {
            // Arrange
            val ctx = TestData.MockContext(
                oppfolgingsperiodeId = UUID.randomUUID(),
                deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET),
            )
            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns listOf(ctx.melding)
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode

            // Act
            val aktivitetskort = aktivitetskortService.lagAktivitetskort(ctx.deltakerModel())

            // Assert
            verify(exactly = 1) { meldingRepository.upsert(any()) }
            aktivitetskort?.id shouldNotBe ctx.aktivitetskort.id
        }

        @Test
        fun `lagAktivitetskort - gamle arenameldinger fra tidligere oppfølgingsperiode - ignorerer`() {
            // Arrange
            val ctx = TestData.MockContext(
                deltaker = TestData.lagDeltaker(
                    kilde = Kilde.ARENA,
                    status = DeltakerStatusModel(DeltakerStatus.Type.FULLFORT, null, nyPeriode.startDato.minusDays(10)),
                ),
            )
            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode

            // Act
            val aktivitetskort = aktivitetskortService.lagAktivitetskort(ctx.deltakerModel())

            // Assert
            aktivitetskort shouldBe null
        }
    }

    @Nested
    inner class OppdaterAktivitetskortDeltakerliste {
        @Test
        fun `oppdaterAktivitetskort(deltakerliste) - meldinger finnes - lager nye aktivitetskort`() {
            // Arrange
            val ctx = TestData.MockContext()
            ctx.stubDeltakerFraAmtDeltaker()

            every { meldingRepository.getByDeltakerlisteId(ctx.deltakerliste.id) } returns listOf(ctx.melding)
            every { deltakerRepository.get(ctx.deltaker.id) } returns ctx.deltaker
            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode
            val lagredeMeldinger = mutableListOf<Melding>()
            every { meldingRepository.upsert(capture(lagredeMeldinger)) } returns Unit

            // Act
            aktivitetskortService.oppdaterAktivitetskort(ctx.deltakerliste.id)

            // Assert
            verify(exactly = 1) { meldingRepository.upsert(any()) }

            lagredeMeldinger.map { it.aktivitetskort } shouldBe listOf(ctx.aktivitetskort)
        }
    }

    @Nested
    inner class OppdaterAktivitetskortArrangor {
        @Test
        fun `oppdaterAktivitetskort(arrangor) - meldinger finnes, deltaker er aktiv - oppdaterer aktivitetskort`() {
            // Arrange
            val ctx = TestData.MockContext()
            val deltakerSluttdato = LocalDate.now().plusWeeks(3)
            val mockAktivitetskort = ctx.aktivitetskort.copy(sluttDato = deltakerSluttdato)
            ctx.stubDeltakerFraAmtDeltaker(ctx.deltaker.copy(sluttdato = deltakerSluttdato))

            every {
                meldingRepository.getByArrangorId(ctx.arrangor.id)
            } returns listOf(
                ctx.melding.copy(
                    aktivitetskort = mockAktivitetskort,
                ),
            )
            every { deltakerRepository.get(ctx.deltaker.id) } returns ctx.deltaker.copy(sluttdato = deltakerSluttdato)
            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { arrangorRepository.getUnderordnedeArrangorer(ctx.arrangor.id) } returns emptyList()
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode
            val lagredeMeldinger = mutableListOf<Melding>()
            every { meldingRepository.upsert(capture(lagredeMeldinger)) } returns Unit

            // Act
            aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor)

            // Assert
            verify(exactly = 1) { meldingRepository.upsert(any()) }

            lagredeMeldinger.map { it.aktivitetskort } shouldBe listOf(mockAktivitetskort)
        }

        @Test
        fun `meldinger finnes, deltaker er aktiv, arrangor har underarrangorer - lager nye aktivitetskort`() {
            // Arrange
            val ctx = TestData.MockContext()
            val ctxUnderarrangor = TestData.MockContext()
            val deltakerSluttdato = LocalDate.now().plusWeeks(3)
            val mockAktivitetskort = ctx.aktivitetskort.copy(sluttDato = deltakerSluttdato)
            val mockAktivitetskortUnderarrangor = ctxUnderarrangor.aktivitetskort.copy(sluttDato = deltakerSluttdato)
            val underarrangor =
                ctxUnderarrangor.arrangor.copy(navn = "Underordnet arrangør", overordnetArrangorId = ctx.arrangor.id)
            val underarrangorMelding = ctxUnderarrangor.melding.copy(
                aktivitetskort = mockAktivitetskortUnderarrangor,
            )
            ctx.stubDeltakerFraAmtDeltaker(ctx.deltaker.copy(sluttdato = deltakerSluttdato))
            ctxUnderarrangor.stubDeltakerFraAmtDeltaker(ctxUnderarrangor.deltaker.copy(sluttdato = deltakerSluttdato))

            every {
                meldingRepository.getByArrangorId(ctx.arrangor.id)
            } returns listOf(
                ctx.melding.copy(
                    aktivitetskort = mockAktivitetskort,
                ),
            )
            every { meldingRepository.getByArrangorId(underarrangor.id) } returns listOf(underarrangorMelding)
            every { meldingRepository.getByDeltakerId(ctxUnderarrangor.deltaker.id) } returns emptyList()
            every { meldingRepository.getByDeltakerId(ctx.deltaker.id) } returns emptyList()

            every { deltakerRepository.get(ctx.deltaker.id) } returns ctx.deltaker.copy(sluttdato = deltakerSluttdato)
            every {
                deltakerRepository.get(ctxUnderarrangor.deltaker.id)
            } returns ctxUnderarrangor.deltaker.copy(
                sluttdato = deltakerSluttdato,
            )
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { deltakerlisteRepository.get(ctxUnderarrangor.deltakerliste.id) } returns ctxUnderarrangor.deltakerliste.copy(
                arrangorId = underarrangor.id,
            )
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { arrangorRepository.get(underarrangor.id) } returns underarrangor
            every { arrangorRepository.getUnderordnedeArrangorer(ctx.arrangor.id) } returns listOf(underarrangor)
            every { aktivitetArenaAclClient.getAktivitetIdForArenaId(1L) } returns mockAktivitetskort.id
            every { aktivitetArenaAclClient.getAktivitetIdForArenaId(2L) } returns mockAktivitetskortUnderarrangor.id
            every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns nyPeriode
            val lagredeMeldinger = mutableListOf<Melding>()
            every { meldingRepository.upsert(capture(lagredeMeldinger)) } returns Unit

            // Act
            aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor)

            // Assert
            verify(exactly = 2) { meldingRepository.upsert(any()) }

            lagredeMeldinger.map { it.aktivitetskort } shouldBe listOf(mockAktivitetskort, mockAktivitetskortUnderarrangor)
        }

        @Test
        fun `oppdaterAktivitetskort(arrangor) - meldinger finnes, deltaker er ikke aktiv - lager ikke nye aktivitetskort`() {
            // Arrange
            val ctx = TestData.MockContext()
            val deltakerSluttdato = LocalDate.now().minusWeeks(3)
            val mockAktivitetskort = ctx.aktivitetskort.copy(sluttDato = deltakerSluttdato)
            ctx.stubDeltakerFraAmtDeltaker(ctx.deltaker.copy(sluttdato = deltakerSluttdato))

            every {
                meldingRepository.getByArrangorId(ctx.arrangor.id)
            } returns listOf(
                ctx.melding.copy(
                    aktivitetskort = mockAktivitetskort,
                ),
            )
            every { deltakerRepository.get(ctx.deltaker.id) } returns ctx.deltaker.copy(sluttdato = deltakerSluttdato)
            every { deltakerlisteRepository.get(ctx.deltakerliste.id) } returns ctx.deltakerliste
            every { arrangorRepository.get(ctx.arrangor.id) } returns ctx.arrangor
            every { arrangorRepository.getUnderordnedeArrangorer(ctx.arrangor.id) } returns emptyList()

            // Act
            aktivitetskortService.oppdaterAktivitetskort(ctx.arrangor)

            // Assert
            verify(exactly = 0) { meldingRepository.upsert(any()) }
        }
    }
}
