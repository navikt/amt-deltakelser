package no.nav.amt.deltaker.enkeltplass

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
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
import java.time.LocalDateTime

class EnkeltplassServiceIntegrationTest : IntegrationTestWithDbBase() {
    val sistEndretAvNavEnhet = lagNavEnhet()
    val sistEndretAvNavAnsatt = lagNavAnsatt(navEnhetId = sistEndretAvNavEnhet.id)
    val navBruker: NavBruker = lagNavBruker(
        navVeilederId = sistEndretAvNavAnsatt.id,
        navEnhetId = sistEndretAvNavEnhet.id,
    )

    val tiltak = TestData.lagTiltakstype(Tiltakskode.ARBEIDSMARKEDSOPPLAERING)

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(sistEndretAvNavEnhet)
        navAnsattRepository.upsert(sistEndretAvNavAnsatt)
        navBrukerRepository.upsert(navBruker)

        sistEndretAvNavAnsatt.navEnhetId?.let {
            coEvery { personServiceClient.hentNavEnhet(it) } returns lagNavEnhet(it)
        }

        coEvery { personServiceClient.hentNavEnhet(sistEndretAvNavEnhet.id) } returns sistEndretAvNavEnhet
        coEvery { personServiceClient.hentNavAnsatt(sistEndretAvNavAnsatt.id) } returns sistEndretAvNavAnsatt
        coEvery { personServiceClient.hentNavBruker(navBruker.personident) } returns navBruker

        TiltakstypeRepository().upsert(tiltak)
    }

    @Nested
    inner class EnkeltplassTests {
        @Test
        fun `opprettKladd - returnerer ny deltakerId`() = runTest {
            val deltaker = enkeltplassService.opprettKladd(
                tiltak.tiltakskode,
                navBruker.personident,
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
                tiltakstype shouldBe tiltak
                navn shouldBe tiltak.navn
                startDato shouldBe null
                sluttDato shouldBe null
                oppstart shouldBe null
                apentForPamelding shouldBe false
                oppmoteSted shouldBe null
                arrangor shouldBe null
                pameldingstype shouldBe null
                status shouldBe GjennomforingStatusType.KLADD
            }
        }

        @Test
        fun `opprettKladd - det finnes allerede kladd på samme enkeltplass tiltakstype - returnerer samme deltakerId`() = runTest {
            val deltaker = enkeltplassService.opprettKladd(
                tiltak.tiltakskode,
                navBruker.personident,
            )

            val deltaker2 = enkeltplassService.opprettKladd(
                tiltak.tiltakskode,
                navBruker.personident,
            )
            deltaker2.id shouldBe deltaker.id
        }

        @Test
        fun `oppdaterKladd - returnerer ny deltakerId`() = runTest {
            // Arrange
            val deltakerInserted = enkeltplassService.opprettKladd(
                tiltak.tiltakskode,
                navBruker.personident,
            )

            val arrangorInTest = lagArrangor()
            arrangorRepository.upsert(arrangorInTest)

            val deltakerExpected = EnkeltplassDeltakerUpdateDbo(
                id = deltakerInserted.id,
                arrangorId = arrangorInTest.id,
                startdato = deltakerInserted.startdato,
                sluttdato = deltakerInserted.sluttdato,
                deltakelsesinnhold = Deltakelsesinnhold(
                    ledetekst = deltakerInserted.deltakerliste.tiltakstype.innhold
                        ?.ledetekst,
                    innhold = listOf(Innhold.createFritekstInnhold("Beskrivelse")),
                ),
            )
            val prisinfoExpected = "Prisinfo"

            // Act
            val oppdatertDeltaker = enkeltplassService.oppdaterKladd(
                deltakerId = deltakerExpected.id,
                startdato = deltakerExpected.startdato,
                sluttdato = deltakerExpected.sluttdato,
                beskrivelse = deltakerExpected.deltakelsesinnhold
                    ?.innhold
                    ?.first()
                    ?.beskrivelse,
                prisinformasjon = prisinfoExpected,
                arrangorUnderenhet = arrangorInTest.organisasjonsnummer,
            )

            // Assert
            oppdatertDeltaker shouldNotBe null

            assertSoftly(oppdatertDeltaker) {
                id shouldBe deltakerExpected.id
                startdato shouldBe deltakerExpected.startdato
                sluttdato shouldBe deltakerExpected.sluttdato
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                vedtaksinformasjon shouldBe null
                sistEndret shouldBeCloseTo LocalDateTime.now()
                kilde shouldBe Kilde.KOMET
                erManueltDeltMedArrangor shouldBe false
                opprettet shouldBeCloseTo LocalDateTime.now()
            }

            assertSoftly(oppdatertDeltaker.status) {
                type shouldBe DeltakerStatus.Type.KLADD
            }

            assertSoftly(oppdatertDeltaker.deltakerliste) {
                gjennomforingstype shouldBe GjennomforingType.Enkeltplass
                tiltakstype shouldBe tiltak
                navn shouldBe tiltak.navn
                prisinformasjon shouldBe prisinfoExpected
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
}
