package no.nav.amt.deltaker.bff.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.deltaker.model.Kladd
import no.nav.amt.deltaker.bff.deltaker.model.Pamelding
import no.nav.amt.deltaker.bff.deltaker.model.Utkast
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.utils.MockResponseHandler
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerKladd
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.deltaker.bff.utils.mockAmtDeltakerClient
import no.nav.amt.deltaker.bff.utils.mockAmtPersonServiceClient
import no.nav.amt.deltaker.bff.utils.mockPaameldingClient
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class PameldingServiceTest {
    private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonServiceClient())
    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetService = NavEnhetService(navEnhetRepository, mockAmtPersonServiceClient())
    private val deltakerRepository = DeltakerRepository()
    private val deltakerService =
        DeltakerService(
            deltakerRepository = deltakerRepository,
            amtDeltakerClient = mockAmtDeltakerClient(),
            navEnhetService = navEnhetService,
            forslagRepository = mockk(),
        )

    private var pameldingService =
        PameldingService(
            deltakerRepository = deltakerRepository,
            deltakerService = deltakerService,
            navBrukerService = NavBrukerService(mockAmtPersonServiceClient(), NavBrukerRepository(), navAnsattService, navEnhetService),
            navEnhetService = navEnhetService,
            paameldingClient = mockPaameldingClient(),
        )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class UpsertKladdTests {
        val kladdDeltakerInTest = lagDeltakerKladd()
        val navEnhetInTest = lagNavEnhet(id = kladdDeltakerInTest.navBruker.navEnhetId!!)
        val kladdInTest =
            Kladd(
                opprinneligDeltaker = kladdDeltakerInTest,
                pamelding =
                    Pamelding(
                        kladdDeltakerInTest.deltakelsesinnhold!!,
                        kladdDeltakerInTest.bakgrunnsinformasjon,
                        kladdDeltakerInTest.deltakelsesprosent,
                        kladdDeltakerInTest.dagerPerUke,
                        endretAv = "Veileder",
                        endretAvEnhet = navEnhetInTest.enhetsnummer,
                    ),
            )

        @Test
        fun `upsertKladd returnerer null nar status er forskjellig fra KLADD`() =
            runTest {
                val kladd =
                    kladdInTest.copy(
                        opprinneligDeltaker =
                            kladdDeltakerInTest.copy(
                                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                            ),
                    )

                pameldingService.upsertKladd(kladd) shouldBe null
            }

        @Test
        fun `upsertKladd oppdaterer deltaker nar status er KLADD`() =
            runTest {
                TestRepository.insert(kladdDeltakerInTest)

                val oppdatertDeltaker = pameldingService.upsertKladd(kladdInTest)

                oppdatertDeltaker.shouldNotBeNull()
                oppdatertDeltaker.id shouldBe kladdDeltakerInTest.id
                oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.KLADD
            }
    }

    @Nested
    inner class OpprettDeltakerTests {
        @Test
        fun `deltaker finnes ikke - oppretter ny deltaker`() =
            runTest {
                val overordnetArrangorInTest = lagArrangor()
                val arrangorInTest = lagArrangor(overordnetArrangorId = overordnetArrangorInTest.id)
                val deltakerListeInTest = lagDeltakerliste(arrangor = arrangorInTest, overordnetArrangor = overordnetArrangorInTest)
                val kladdInTest = lagDeltakerKladd(deltakerliste = deltakerListeInTest)
                val navVeilederInTest = lagNavAnsatt(id = kladdInTest.navBruker.navVeilederId!!)
                val navEnhetInTest = lagNavEnhet(id = kladdInTest.navBruker.navEnhetId!!)
                TestRepository.insert(deltakerListeInTest, overordnetArrangorInTest)

                MockResponseHandler.addOpprettKladdResponse(kladdInTest)
                MockResponseHandler.addNavAnsattResponse(navVeilederInTest)
                MockResponseHandler.addNavEnhetGetResponse(navEnhetInTest)

                val deltaker =
                    pameldingService.opprettDeltaker(
                        deltakerlisteId = deltakerListeInTest.id,
                        personIdent = kladdInTest.navBruker.personident,
                    )

                deltaker.id shouldBe
                    deltakerRepository
                        .getMany(kladdInTest.navBruker.personident, deltakerListeInTest.id)
                        .first()
                        .id

                assertSoftly(deltaker) {
                    status.type shouldBe DeltakerStatus.Type.KLADD
                    startdato shouldBe null
                    sluttdato shouldBe null
                    dagerPerUke shouldBe null
                    deltakelsesprosent shouldBe null
                    bakgrunnsinformasjon shouldBe null
                    deltakelsesinnhold!!.innhold shouldBe emptyList()

                    assertSoftly(it.deltakerliste) {
                        id shouldBe deltakerListeInTest.id
                        navn shouldBe deltakerListeInTest.navn
                        tiltak.tiltakskode shouldBe deltakerListeInTest.tiltak.tiltakskode
                        arrangor.arrangor shouldBe arrangorInTest
                        arrangor.overordnetArrangorNavn shouldBe overordnetArrangorInTest.navn
                        oppstart shouldBe deltakerListeInTest.oppstart
                    }
                }
            }

        @Test
        fun `kall til amt-deltaker feiler - kaster exception`() =
            runTest {
                val personIdent = TestData.randomIdent()
                MockResponseHandler.addOpprettKladdResponse(null)

                val thrown =
                    shouldThrow<IllegalStateException> {
                        pameldingService.opprettDeltaker(UUID.randomUUID(), personIdent)
                    }

                thrown.message shouldBe "Kunne ikke opprette kladd i amt-deltaker. Status=500 error=Noe gikk galt"
            }
    }

    @Test
    fun `upsertUtkast - oppdaterer og returnerer deltaker`() =
        runTest {
            val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING))

            TestRepository.insert(deltaker)
            val navEnhetInTest = navEnhetRepository.get(deltaker.navBruker.navEnhetId!!).shouldNotBeNull()

            val forventetDeltaker =
                deltaker.copy(
                    deltakelsesinnhold =
                        Deltakelsesinnhold(
                            "Beskrivelse",
                            listOf(Innhold("nytt innhold", "nytt-innhold", true, null)),
                        ),
                    bakgrunnsinformasjon = "Noe ny informasjon",
                    deltakelsesprosent = 42F,
                    dagerPerUke = 3F,
                )

            MockResponseHandler.addUtkastResponse(forventetDeltaker)

            val utkast =
                Utkast(
                    deltakerId = deltaker.id,
                    pamelding =
                        Pamelding(
                            forventetDeltaker.deltakelsesinnhold!!,
                            forventetDeltaker.bakgrunnsinformasjon,
                            forventetDeltaker.deltakelsesprosent,
                            forventetDeltaker.dagerPerUke,
                            endretAv = "Veileder",
                            endretAvEnhet = navEnhetInTest.enhetsnummer,
                        ),
                    godkjentAvNav = false,
                )

            val oppdatertDeltaker = pameldingService.upsertUtkast(utkast)

            assertSoftly(oppdatertDeltaker) {
                deltakelsesinnhold shouldBe forventetDeltaker.deltakelsesinnhold
                bakgrunnsinformasjon shouldBe forventetDeltaker.bakgrunnsinformasjon
                deltakelsesprosent shouldBe forventetDeltaker.deltakelsesprosent
                dagerPerUke shouldBe forventetDeltaker.dagerPerUke
            }
        }

    @Nested
    inner class AvbrytUtkast {
        @Test
        fun `avbrytUtkast() - utkast avbrytes for ny deltakelse - Den forrige avsluttede deltakelsen laases opp`() =
            runTest {
                val navEnhet = lagNavEnhet().copy()
                navEnhetRepository.upsert(navEnhet)

                val gammelDeltaker =
                    lagDeltaker(
                        status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                        navBruker = TestData.lagNavBruker(navEnhetId = navEnhet.id),
                    )
                TestRepository.insert(gammelDeltaker)

                val nyDeltaker =
                    lagDeltakerKladd(
                        deltakerliste = gammelDeltaker.deltakerliste,
                        navBruker = gammelDeltaker.navBruker,
                    )
                TestRepository.insert(nyDeltaker)

                val nyDeltakerOppdaterUtkast =
                    Deltakeroppdatering(
                        id = nyDeltaker.id,
                        startdato = null,
                        sluttdato = null,
                        dagerPerUke = null,
                        deltakelsesprosent = 100F,
                        bakgrunnsinformasjon = "Tekst",
                        deltakelsesinnhold = null,
                        status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                        erManueltDeltMedArrangor = false,
                        historikk = emptyList(),
                    )

                deltakerService.oppdaterDeltaker(nyDeltakerOppdaterUtkast)
                deltakerRepository.get(gammelDeltaker.id).getOrThrow().kanEndres shouldBe false

                MockResponseHandler.addNavEnhetPostResponse(navEnhet)
                MockResponseHandler.avbrytUtkastResponse(nyDeltaker)

                pameldingService.avbrytUtkast(nyDeltaker, navEnhet.enhetsnummer, "test")

                val gammelDeltakerFraDb = deltakerRepository.get(gammelDeltaker.id).getOrThrow()
                gammelDeltakerFraDb.kanEndres shouldBe true
            }

        @Test
        fun `avbrytUtkast() - utkast avbrytes for ny deltakelse - Den forrige avsluttede deltakelsen forblir laast`() =
            runTest {
                val navEnhet = lagNavEnhet()
                navEnhetRepository.upsert(navEnhet)

                val gammelDeltaker =
                    lagDeltaker(
                        status = lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT),
                        navBruker = TestData.lagNavBruker(navEnhetId = navEnhet.id),
                    )
                TestRepository.insert(gammelDeltaker)

                val nyDeltaker =
                    lagDeltakerKladd(
                        deltakerliste = gammelDeltaker.deltakerliste,
                        navBruker = gammelDeltaker.navBruker,
                    )
                TestRepository.insert(nyDeltaker)

                val nyDeltakerOppdaterUtkast =
                    Deltakeroppdatering(
                        id = nyDeltaker.id,
                        startdato = null,
                        sluttdato = null,
                        dagerPerUke = null,
                        deltakelsesprosent = 100F,
                        bakgrunnsinformasjon = "Tekst",
                        deltakelsesinnhold = null,
                        status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                        erManueltDeltMedArrangor = false,
                        historikk = emptyList(),
                    )

                deltakerService.oppdaterDeltaker(nyDeltakerOppdaterUtkast)
                deltakerRepository.get(gammelDeltaker.id).getOrThrow().kanEndres shouldBe false

                MockResponseHandler.avbrytUtkastResponse(nyDeltaker)

                pameldingService.avbrytUtkast(nyDeltaker, navEnhet.enhetsnummer, "test")

                val gammelDeltakerFraDb = deltakerRepository.get(gammelDeltaker.id).getOrThrow()
                gammelDeltakerFraDb.kanEndres shouldBe false
            }
    }
}
