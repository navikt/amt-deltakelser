package no.nav.amt.deltaker.bff.navtiltakskoordinator

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.vurdering.VurderingService
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakskoordinatorDeltakerResponse
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class TiltakskoordinatorServiceIntegrationTest {
    private val amtDeltakerClient = mockk<AmtDeltakerClient>()
    private val tiltaksKoordinatorClient = mockk<TiltakskoordinatorClient>()

    private val navEnhetService = mockk<NavEnhetService>()
    private val navAnsattService = mockk<NavAnsattService>()
    private val vurderingService = mockk<VurderingService>()
    private val forslagRepository = mockk<ForslagRepository>()
    private val deltakerRepository = DeltakerRepository()
    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        amtDeltakerClient = amtDeltakerClient,
        forslagRepository = forslagRepository,
    )
    private val amtDistribusjonClient = mockk<AmtDistribusjonClient>()
    private val ulestHendelseRepository = mockk<UlestHendelseRepository>()
    private val tiltakskoordinatorService = TiltakskoordinatorService(
        tiltaksKoordinatorClient,
        deltakerRepository,
        deltakerService,
        vurderingService,
        navEnhetService,
        navAnsattService,
        amtDistribusjonClient,
        forslagRepository,
        ulestHendelseRepository,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `tildelPlass - returnerer og lagrer deltaker med ny status`() = runTest {
        val deltaker = lagDeltaker()
        val navAnsatt = lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)

        TestRepository.insert(deltaker)

        val nyStatus = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)

        coEvery {
            tiltaksKoordinatorClient.tildelPlass(listOf(deltaker.id), navAnsatt.navIdent)
        } returns listOf(lagTiltakskoordinatorDeltakerResponse(id = deltaker.id, status = nyStatus))

        val resultatFraAmtDeltaker = tiltakskoordinatorService.endreDeltakere(
            deltakerIder = listOf(deltaker.id),
            endring = EndringFraTiltakskoordinator.TildelPlass,
            endretAv = navAnsatt.navIdent,
        )

        resultatFraAmtDeltaker.size shouldBe 1
        val resultDeltaker = resultatFraAmtDeltaker.first()
        resultDeltaker.id shouldBe deltaker.id
        resultDeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        resultDeltaker.feilkode shouldBe null
    }

    @Test
    fun `settPaaVenteliste - returnerer og lagrer deltaker med ny status`() = runTest {
        val deltaker = lagDeltaker()
        val navAnsatt = lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)

        TestRepository.insert(deltaker)

        val nyStatus = lagDeltakerStatus(DeltakerStatus.Type.VENTELISTE)

        coEvery {
            tiltaksKoordinatorClient.settPaaVenteliste(listOf(deltaker.id), navAnsatt.navIdent)
        } returns listOf(lagTiltakskoordinatorDeltakerResponse(id = deltaker.id, status = nyStatus))

        val resultatFraAmtDeltaker = tiltakskoordinatorService.endreDeltakere(
            listOf(deltaker.id),
            EndringFraTiltakskoordinator.SettPaaVenteliste,
            navAnsatt.navIdent,
        )

        resultatFraAmtDeltaker.size shouldBe 1
        val resultDeltaker = resultatFraAmtDeltaker.first()
        resultDeltaker.id shouldBe deltaker.id
        resultDeltaker.status.type shouldBe DeltakerStatus.Type.VENTELISTE
        resultDeltaker.feilkode shouldBe null
    }

    @Test
    fun `settPaaVenteliste - en deltaker feiler i amt-deltaker - returnerer deltaker med feilkode`() = runTest {
        val deltaker = lagDeltaker()
        val navAnsatt = lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)

        TestRepository.insert(deltaker)

        val nyStatus = lagDeltakerStatus(DeltakerStatus.Type.VENTELISTE)

        coEvery {
            tiltaksKoordinatorClient.settPaaVenteliste(listOf(deltaker.id), navAnsatt.navIdent)
        } returns listOf(
            lagTiltakskoordinatorDeltakerResponse(
                id = deltaker.id,
                status = nyStatus,
                feilkode = DeltakerOppdateringFeilkode.UKJENT,
            ),
        )

        val resultatFraAmtDeltaker = tiltakskoordinatorService.endreDeltakere(
            listOf(deltaker.id),
            EndringFraTiltakskoordinator.SettPaaVenteliste,
            navAnsatt.navIdent,
        )

        resultatFraAmtDeltaker.size shouldBe 1
        val resultDeltaker = resultatFraAmtDeltaker.first()
        resultDeltaker.id shouldBe deltaker.id
        resultDeltaker.status.type shouldBe DeltakerStatus.Type.VENTELISTE
        resultDeltaker.feilkode shouldBe DeltakerOppdateringFeilkode.UKJENT
    }
}
