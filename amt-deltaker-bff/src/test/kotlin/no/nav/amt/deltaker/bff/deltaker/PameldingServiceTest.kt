package no.nav.amt.deltaker.bff.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.innbygger.NavBrukerRepository
import no.nav.amt.deltaker.bff.innbygger.NavBrukerService
import no.nav.amt.deltaker.bff.model.Kladd
import no.nav.amt.deltaker.bff.model.Pamelding
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerKladd
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.internapi.paamelding.response.OpprettKladdResponse
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class PameldingServiceTest {
    private val amtPersonServiceClient: AmtPersonServiceClient = mockk(relaxed = true)
    private val navAnsattService = NavAnsattService(
        repository = NavAnsattRepository(),
        amtPersonServiceClient = amtPersonServiceClient,
    )
    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetService = NavEnhetService(
        repository = navEnhetRepository,
        amtPersonServiceClient = amtPersonServiceClient,
    )
    private val deltakerRepository = DeltakerRepository()
    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        amtDeltakerClient = mockk(relaxed = true),
        navEnhetService = navEnhetService,
        forslagRepository = mockk(),
    )

    private val paameldingClient: PaameldingClient = mockk(relaxed = true)

    private var pameldingService = PameldingService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        navBrukerService = NavBrukerService(
            amtPersonServiceClient = mockk(relaxed = true),
            repository = NavBrukerRepository(),
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
        ),
        paameldingClient = paameldingClient,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() = clearAllMocks()

    @Nested
    inner class UpsertKladdTests {
        val kladdDeltakerInTest = lagDeltakerKladd()
        val navEnhetInTest = lagNavEnhet(id = kladdDeltakerInTest.navBruker.navEnhetId!!)
        val kladdInTest = Kladd(
            opprinneligDeltaker = kladdDeltakerInTest,
            pamelding = Pamelding(
                kladdDeltakerInTest.deltakelsesinnhold!!,
                kladdDeltakerInTest.bakgrunnsinformasjon,
                kladdDeltakerInTest.deltakelsesprosent,
                kladdDeltakerInTest.dagerPerUke,
                endretAv = "Veileder",
                endretAvEnhet = navEnhetInTest.enhetsnummer,
            ),
        )

        @Test
        fun `upsertKladd returnerer null nar status er forskjellig fra KLADD`() = runTest {
            val kladd = kladdInTest.copy(
                opprinneligDeltaker = kladdDeltakerInTest.copy(
                    status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                ),
            )

            pameldingService.upsertKladd(kladd) shouldBe null
        }

        @Test
        fun `upsertKladd oppdaterer deltaker nar status er KLADD`() = runTest {
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
        fun `deltaker finnes ikke - oppretter ny deltaker`() = runTest {
            val overordnetArrangorInTest = lagArrangor()
            val arrangorInTest = lagArrangor(overordnetArrangorId = overordnetArrangorInTest.id)
            val deltakerListeInTest = lagDeltakerliste(arrangor = arrangorInTest, overordnetArrangor = overordnetArrangorInTest)

            val kladdInTest = lagDeltakerKladd(deltakerliste = deltakerListeInTest)

            TestRepository.insert(kladdInTest)
            TestRepository.insert(deltakerListeInTest, overordnetArrangorInTest)

            coEvery { paameldingClient.opprettKladd(any(), any()) } returns OpprettKladdResponse(
                id = kladdInTest.id,
                navBruker = kladdInTest.navBruker,
                deltakerlisteId = kladdInTest.deltakerliste.id,
                startdato = kladdInTest.startdato,
                sluttdato = kladdInTest.sluttdato,
                dagerPerUke = kladdInTest.dagerPerUke,
                deltakelsesprosent = kladdInTest.deltakelsesprosent,
                bakgrunnsinformasjon = kladdInTest.bakgrunnsinformasjon,
                deltakelsesinnhold = kladdInTest.deltakelsesinnhold!!,
                status = kladdInTest.status,
            )

            val deltaker = pameldingService.opprettKladd(
                deltakerlisteId = deltakerListeInTest.id,
                personIdent = kladdInTest.navBruker.personident,
            )

            deltaker.id shouldBe deltakerRepository
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
        fun `kall til amt-deltaker feiler - kaster exception`() = runTest {
            val personIdent = randomIdent()

            coEvery { paameldingClient.opprettKladd(any(), any()) } throws
                IllegalStateException("Kunne ikke opprette kladd i amt-deltaker. Status=500 error=Noe gikk galt")

            val thrown = shouldThrow<IllegalStateException> {
                pameldingService.opprettKladd(UUID.randomUUID(), personIdent)
            }

            thrown.message shouldBe "Kunne ikke opprette kladd i amt-deltaker. Status=500 error=Noe gikk galt"
        }
    }
}
