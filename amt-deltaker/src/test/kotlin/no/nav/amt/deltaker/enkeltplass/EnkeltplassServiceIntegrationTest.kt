package no.nav.amt.deltaker.enkeltplass

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EnkeltplassServiceIntegrationTest : IntegrationTestWithDbBase() {
    val sistEndretAvNavEnhet = lagNavEnhet()
    val sistEndretAvNavAnsatt = lagNavAnsatt(navEnhetId = sistEndretAvNavEnhet.id)
    val navBrukerInTest: NavBruker = lagNavBruker(
        navVeilederId = sistEndretAvNavAnsatt.id,
        navEnhetId = sistEndretAvNavEnhet.id,
    )

    val tiltakInTest = TestData.lagTiltakstype(Tiltakskode.ARBEIDSMARKEDSOPPLAERING)

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(sistEndretAvNavEnhet)
        navAnsattRepository.upsert(sistEndretAvNavAnsatt)
        navBrukerRepository.upsert(navBrukerInTest)

        sistEndretAvNavAnsatt.navEnhetId?.let {
            coEvery { personServiceClient.hentNavEnhet(it) } returns lagNavEnhet(it)
        }

        coEvery { personServiceClient.hentNavEnhet(sistEndretAvNavEnhet.id) } returns sistEndretAvNavEnhet
        coEvery { personServiceClient.hentNavAnsatt(sistEndretAvNavAnsatt.id) } returns sistEndretAvNavAnsatt
        coEvery { personServiceClient.hentNavBruker(navBrukerInTest.personident) } returns navBrukerInTest

        coEvery { kodeverkClient.hentKodeverk(any()) } returns OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = emptyList(),
            sertifiseringValg = emptySet(),
        )

        TiltakRepository().upsert(tiltakInTest)
    }

    @Nested
    inner class OpprettKladdTests {
        @Test
        fun `opprettKladd - returnerer ny deltakerId`() = runTest {
            val deltaker = enkeltplassService.opprettKladd(
                tiltakInTest.tiltakskode,
                navBrukerInTest.personident,
            )

            assertSoftly(deltaker) {
                id shouldBe deltaker.id
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                vedtaksinformasjon shouldBe null
                sistEndret shouldBeCloseTo LocalDateTime.now()
                kilde shouldBe Kilde.KOMET
                erManueltDeltMedArrangor shouldBe false
                opprettet shouldBeCloseTo LocalDateTime.now()
            }

            assertSoftly(deltaker.status) {
                type shouldBe DeltakerStatus.Type.KLADD
            }

            assertSoftly(deltaker.deltakerliste) {
                gjennomforingstype shouldBe GjennomforingType.Enkeltplass
                tiltakstype shouldBe tiltakInTest
                navn shouldBe tiltakInTest.navn
                startDato shouldBe null
                sluttDato shouldBe null
                oppstart shouldBe Oppstartstype.ENKELTPLASS
                apentForPamelding shouldBe false
                oppmoteSted shouldBe null
                arrangor shouldBe null
                pameldingstype shouldBe GjennomforingPameldingType.TRENGER_GODKJENNING
                status shouldBe GjennomforingStatusType.KLADD
            }
        }

        @Test
        fun `opprettKladd - det finnes allerede kladd paa samme enkeltplass tiltakstype - returnerer samme deltakerId`() = runTest {
            val deltaker = enkeltplassService.opprettKladd(
                tiltakInTest.tiltakskode,
                navBrukerInTest.personident,
            )

            val deltaker2 = enkeltplassService.opprettKladd(
                tiltakInTest.tiltakskode,
                navBrukerInTest.personident,
            )
            deltaker2.id shouldBe deltaker.id
        }
    }

    @Nested
    inner class OppdaterKladdTests {
        @Test
        fun `oppdaterKladd - lagrer kladd`() = runTest {
            // Arrange
            val deltakerInserted = enkeltplassService.opprettKladd(
                tiltakInTest.tiltakskode,
                navBrukerInTest.personident,
            )

            val arrangorInTest = lagArrangor()
            arrangorRepository.upsert(arrangorInTest)

            val expectedDeltaker = EnkeltplassDeltakerUpdateDbo(
                id = deltakerInserted.id,
                startdato = deltakerInserted.startdato,
                sluttdato = deltakerInserted.sluttdato,
                deltakelsesinnhold = Deltakelsesinnhold(
                    ledetekst = deltakerInserted.deltakerliste.tiltakstype.innhold
                        ?.ledetekst,
                    innhold = listOf(Innhold.createFritekstInnhold("Beskrivelse")),
                ),
            )

            val oppdaterKladdRequest = OppdaterEnkeltplassKladdRequest(
                beskrivelse = expectedDeltaker.deltakelsesinnhold
                    .shouldNotBeNull()
                    .innhold
                    .first()
                    .beskrivelse,
                startdato = expectedDeltaker.startdato,
                sluttdato = expectedDeltaker.sluttdato,
                arrangorUnderenhet = arrangorInTest.organisasjonsnummer,
                prisinformasjon = Prisinformasjon.Anskaffelse(
                    pris = 42,
                ),
            )

            // Act
            enkeltplassService.oppdaterKladd(
                deltakerId = expectedDeltaker.id,
                oppdaterKladdRequest,
            )

            // Assert
            val deltakerUpdated = deltakerRepository.get(expectedDeltaker.id).shouldBeSuccess()

            assertSoftly(deltakerUpdated) {
                id shouldBe expectedDeltaker.id
                startdato shouldBe expectedDeltaker.startdato
                sluttdato shouldBe expectedDeltaker.sluttdato
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                vedtaksinformasjon shouldBe null
                sistEndret shouldBeCloseTo LocalDateTime.now()
                kilde shouldBe Kilde.KOMET
            }
        }

        @Test
        fun `oppdaterKladd - uten beskrivelse - lagrer tom innhold-liste`() = runTest {
            val deltakerInserted = enkeltplassService.opprettKladd(
                tiltakInTest.tiltakskode,
                navBrukerInTest.personident,
            )

            val oppdaterKladdRequest = OppdaterEnkeltplassKladdRequest(
                beskrivelse = null,
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusDays(10),
                arrangorUnderenhet = null,
                prisinformasjon = null,
            )

            enkeltplassService.oppdaterKladd(
                deltakerId = deltakerInserted.id,
                oppdaterKladdRequest,
            )

            val deltakerUpdated = deltakerRepository.get(deltakerInserted.id).shouldBeSuccess()
            assertSoftly(deltakerUpdated) {
                deltakelsesinnhold.shouldNotBeNull().innhold shouldBe emptyList()
                startdato shouldBe oppdaterKladdRequest.startdato
                sluttdato shouldBe oppdaterKladdRequest.sluttdato
            }
        }

        @Test
        fun `oppdaterKladd - deltaker er ikke KLADD - kaster exception`() = runTest {
            val arrangorInTest = lagArrangor()
            arrangorRepository.upsert(arrangorInTest)

            val deltaker = lagDeltaker(
                navBruker = navBrukerInTest,
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                deltakerliste = lagDeltakerliste(
                    arrangor = arrangorInTest,
                    tiltakstype = tiltakInTest,
                    gjennomforingstype = GjennomforingType.Enkeltplass,
                    status = GjennomforingStatusType.KLADD,
                ),
            )
            arrangorRepository.upsert(arrangorInTest)
            deltakerlisteRepository.upsert(deltaker.deltakerliste)
            deltakerRepository.upsert(deltaker)
            DeltakerStatusRepository.lagreStatus(deltaker.id, deltaker.status)

            shouldThrow<IllegalArgumentException> {
                enkeltplassService.oppdaterKladd(
                    deltakerId = deltaker.id,
                    oppdaterKladdRequest = OppdaterEnkeltplassKladdRequest(
                        beskrivelse = null,
                        prisinformasjon = null,
                        startdato = null,
                        sluttdato = null,
                        arrangorUnderenhet = null,
                    ),
                )
            }
        }
    }

    @Nested
    inner class UtkastTests {
        private val pameldingRequestInTest = EnkeltplassPameldingRequest(
            beskrivelse = "Testbeskrivelse",
            arrangorUnderenhet = "987654321",
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusDays(1),
            prisinformasjon = Prisinformasjon.Anskaffelse(
                pris = 42,
            ),
        )

        private val decoratedRequest = EnkeltplassPameldingDecoratedRequest(
            wrappedRequest = pameldingRequestInTest,
            endretAvEnhet = sistEndretAvNavEnhet.enhetsnummer,
            endretAv = sistEndretAvNavAnsatt.navIdent,
        )

        @Test
        fun `del utkast - lagrer utkast`() = runTest {
            // Arrange
            val arrangorInTest = lagArrangor(organisasjonsnummer = pameldingRequestInTest.arrangorUnderenhet)
            arrangorRepository.upsert(arrangorInTest)

            val deltakerInTest = enkeltplassService.opprettKladd(
                tiltakInTest.tiltakskode,
                navBrukerInTest.personident,
            )

            // Act
            val oppdatertDeltaker = enkeltplassService.delUtkastMedInnbygger(
                deltakerId = deltakerInTest.id,
                decoratedRequest = decoratedRequest,
            )

            // Assert
            assertSoftly(oppdatertDeltaker) {
                id shouldBe deltakerInTest.id
                startdato shouldBe pameldingRequestInTest.startdato
                sluttdato shouldBe pameldingRequestInTest.sluttdato
                sistEndret shouldBeCloseTo LocalDateTime.now()
            }

            assertSoftly(oppdatertDeltaker.status) {
                type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
            }

            assertSoftly(oppdatertDeltaker.vedtaksinformasjon) {
                this.shouldNotBeNull().fattet shouldBe null
                fattetAvNav shouldBe false
                opprettet shouldBeCloseTo LocalDateTime.now()
            }

            assertSoftly(oppdatertDeltaker.deltakerliste) {
                gjennomforingstype shouldBe GjennomforingType.Enkeltplass
                tiltakstype shouldBe tiltakInTest
                navn shouldBe tiltakInTest.navn
                arrangor shouldBe arrangorInTest
            }
        }

        @Test
        fun `meld paa direkte - lager ferdig pamelding med status sokt inn`() = runTest {
            // Arrange
            val arrangorInTest = lagArrangor(organisasjonsnummer = pameldingRequestInTest.arrangorUnderenhet)
            arrangorRepository.upsert(arrangorInTest)

            val deltakerInTest = enkeltplassService.opprettKladd(
                tiltakInTest.tiltakskode,
                navBrukerInTest.personident,
            )

            // Act
            enkeltplassService.meldPaaDirekte(
                deltakerId = deltakerInTest.id,
                decoratedRequest = decoratedRequest,
            )

            // Assert
            val oppdatertDeltaker = deltakerRepository.get(deltakerInTest.id).shouldBeSuccess()
            assertSoftly(oppdatertDeltaker) {
                id shouldBe deltakerInTest.id
                startdato shouldBe pameldingRequestInTest.startdato
                sluttdato shouldBe pameldingRequestInTest.sluttdato
                sistEndret shouldBeCloseTo LocalDateTime.now()
            }

            assertSoftly(oppdatertDeltaker.status) {
                type shouldBe DeltakerStatus.Type.SOKT_INN
            }

            assertSoftly(oppdatertDeltaker.vedtaksinformasjon) {
                this.shouldNotBeNull().fattet shouldBeCloseTo null
                fattetAvNav shouldBe false
                opprettet shouldBeCloseTo LocalDateTime.now()
            }

            assertSoftly(oppdatertDeltaker.deltakerliste) {
                gjennomforingstype shouldBe GjennomforingType.Enkeltplass
                tiltakstype shouldBe tiltakInTest
                navn shouldBe tiltakInTest.navn
                arrangor shouldBe arrangorInTest
            }
        }

        @Test
        fun `oppdater utkast - lagrer utkast`() = runTest {
            // Arrange
            val arrangorInTest = lagArrangor(organisasjonsnummer = pameldingRequestInTest.arrangorUnderenhet)
            val deltaker = lagDeltaker(
                navBruker = navBrukerInTest,
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                deltakerliste = lagDeltakerliste(
                    arrangor = arrangorInTest,
                    tiltakstype = tiltakInTest,
                    gjennomforingstype = GjennomforingType.Enkeltplass,
                    status = GjennomforingStatusType.KLADD,
                    navn = tiltakInTest.navn,
                ),
            )
            arrangorRepository.upsert(arrangorInTest)
            deltakerlisteRepository.upsert(deltaker.deltakerliste)
            deltakerRepository.upsert(deltaker)
            DeltakerStatusRepository.lagreStatus(deltaker.id, deltaker.status)

            val vedtak = TestData.lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                fattetAvNav = false,
                opprettetAv = sistEndretAvNavAnsatt,
                opprettetAvEnhet = sistEndretAvNavEnhet,
            )
            vedtakRepository.upsert(vedtak)

            // Act
            val oppdatertDeltaker = enkeltplassService.oppdaterUtkast(
                deltakerId = deltaker.id,
                decoratedRequest = decoratedRequest,
            )

            // Assert
            assertSoftly(oppdatertDeltaker) {
                id shouldBe deltaker.id
                startdato shouldBe pameldingRequestInTest.startdato
                sluttdato shouldBe pameldingRequestInTest.sluttdato
                sistEndret shouldBeCloseTo LocalDateTime.now()
            }

            assertSoftly(oppdatertDeltaker.status) {
                type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
            }

            assertSoftly(oppdatertDeltaker.vedtaksinformasjon) {
                this.shouldNotBeNull().fattet shouldBe null
                fattetAvNav shouldBe false
                opprettet shouldBeCloseTo LocalDateTime.now()
            }

            assertSoftly(oppdatertDeltaker.deltakerliste) {
                gjennomforingstype shouldBe GjennomforingType.Enkeltplass
                tiltakstype shouldBe tiltakInTest
                navn shouldBe tiltakInTest.navn
                arrangor shouldBe arrangorInTest
            }
        }
    }
    /*
        Slett kladd ligger fortsatt i pamelingService, uavhengig av om det er enkeltplass. Splitte opp?
            @Test
            fun `slettKladd - deltaker er KLADD - sletter deltaker og gjennomføring`() = runTest {
                val deltakerInserted = enkeltplassService.opprettKladd(
                    tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                    navBruker.personident,
                )

                pameldingService.slettKladd(deltakerInserted.id)

                deltakerRepository.get(deltakerInserted.id).shouldBeFailure()
                deltakerlisteRepository.get(deltakerInserted.deltakerliste.id).shouldBeFailure()
            }
            @Test
            fun `slettKladd - deltaker er KLADD men deltakerliste er syncet med valp - sletter ikke`() = runTest {
                val deltakerInserted = enkeltplassService.opprettKladd(
                    tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                    navBruker.personident,
                )
                deltakerlisteRepository.upsert(deltakerInserted.deltakerliste.copy(status = GjennomforingStatusType.GJENNOMFORES))

                shouldThrowAny {
                    pameldingService.slettKladd(deltakerInserted.id)
                }
                deltakerRepository.get(deltakerInserted.id).shouldBeSuccess()
                deltakerlisteRepository.get(deltakerInserted.deltakerliste.id).shouldBeSuccess()
            }
     */
}
