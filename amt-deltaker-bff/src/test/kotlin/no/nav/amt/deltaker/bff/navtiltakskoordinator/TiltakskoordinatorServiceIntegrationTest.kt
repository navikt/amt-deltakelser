package no.nav.amt.deltaker.bff.navtiltakskoordinator

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.DtoMappers.deltakerOppdateringResponseFromDeltaker
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.vurdering.VurderingService
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

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
        val navEnhet = lagNavEnhet(id = deltaker.navBruker.navEnhetId!!)
        val navAnsatt = lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)

        TestRepository.insert(deltaker)
        every { vurderingService.getSisteVurderingForDeltaker(deltaker.id) } returns null
        every { navEnhetService.hentEnheter(listOf(navEnhet.id)) } returns mapOf(navEnhet.id to navEnhet)
        every { navAnsattService.hentAnsatte(listOf(navAnsatt.id)) } returns mapOf(navAnsatt.id to navAnsatt)
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        every { forslagRepository.getForDeltakere(any()) } returns emptyList()
        every { ulestHendelseRepository.getForDeltaker(any()) } returns emptyList()

        val nyStatus =
            DeltakerStatus(UUID.randomUUID(), DeltakerStatus.Type.VENTER_PA_OPPSTART, null, LocalDateTime.now(), null, LocalDateTime.now())

        coEvery {
            tiltaksKoordinatorClient.tildelPlass(listOf(deltaker.id), navAnsatt.navIdent)
        } returns listOf(deltakerOppdateringResponseFromDeltaker(deltaker.copy(status = nyStatus)))

        val resultatFraAmtDeltaker = tiltakskoordinatorService.endreDeltakere(
            deltakerIder = listOf(deltaker.id),
            endring = EndringFraTiltakskoordinator.TildelPlass,
            endretAv = navAnsatt.navIdent,
        )
        val resultDeltaker = resultatFraAmtDeltaker.first()
        resultatFraAmtDeltaker.size shouldBe 1
        resultDeltaker.status.id shouldNotBe deltaker.status.id
        resultDeltaker.status.trimMss().copy(id = nyStatus.id) shouldBe nyStatus.trimMss()

        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(navAnsatt.id) } returns navAnsatt
        every { navEnhetService.hentEnhet(navEnhet.id) } returns navEnhet
    }

    @Test
    fun `settPaaVenteliste - returnerer og lagrer deltaker med ny status`() = runTest {
        val deltaker = lagDeltaker()
        val navEnhet = lagNavEnhet(id = deltaker.navBruker.navEnhetId!!)
        val navAnsatt = lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)

        TestRepository.insert(deltaker)
        every { vurderingService.getSisteVurderingForDeltaker(deltaker.id) } returns null
        every { navEnhetService.hentEnheter(listOf(navEnhet.id)) } returns mapOf(navEnhet.id to navEnhet)
        every { navAnsattService.hentAnsatte(listOf(navAnsatt.id)) } returns mapOf(navAnsatt.id to navAnsatt)
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        every { forslagRepository.getForDeltakere(any()) } returns emptyList()
        every { ulestHendelseRepository.getForDeltaker(any()) } returns emptyList()

        val nyStatus = DeltakerStatus(
            id = UUID.randomUUID(),
            type = DeltakerStatus.Type.VENTELISTE,
            aarsak = null,
            gyldigFra = LocalDateTime.now(),
            gyldigTil = null,
            opprettet = LocalDateTime.now(),
        )

        coEvery {
            tiltaksKoordinatorClient.settPaaVenteliste(listOf(deltaker.id), navAnsatt.navIdent)
        } returns listOf(deltakerOppdateringResponseFromDeltaker(deltaker.copy(status = nyStatus)))

        val resultatFraAmtDeltaker = tiltakskoordinatorService.endreDeltakere(
            listOf(deltaker.id),
            EndringFraTiltakskoordinator.SettPaaVenteliste,
            navAnsatt.navIdent,
        )
        val resultDeltaker = resultatFraAmtDeltaker.first()
        resultatFraAmtDeltaker.size shouldBe 1
        resultDeltaker.status.id shouldNotBe deltaker.status.id
        resultDeltaker.status.trimMss().copy(id = nyStatus.id) shouldBe nyStatus.trimMss()
    }

    @Test
    fun `settPaaVenteliste - en deltaker feiler i amt-deltaker - returnerer deltaker med feilkode`() = runTest {
        val deltaker = lagDeltaker()
        val navEnhet = lagNavEnhet(id = deltaker.navBruker.navEnhetId!!)
        val navAnsatt = lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)

        TestRepository.insert(deltaker)
        every { vurderingService.getSisteVurderingForDeltaker(deltaker.id) } returns null
        every { navEnhetService.hentEnheter(listOf(navEnhet.id)) } returns mapOf(navEnhet.id to navEnhet)
        every { navAnsattService.hentAnsatte(listOf(navAnsatt.id)) } returns mapOf(navAnsatt.id to navAnsatt)
        every { forslagRepository.getForDeltakere(any()) } returns emptyList()
        every { ulestHendelseRepository.getForDeltaker(any()) } returns emptyList()

        val nyStatus =
            DeltakerStatus(UUID.randomUUID(), DeltakerStatus.Type.VENTELISTE, null, LocalDateTime.now(), null, LocalDateTime.now())

        coEvery {
            tiltaksKoordinatorClient.settPaaVenteliste(listOf(deltaker.id), navAnsatt.navIdent)
        } returns listOf(
            deltakerOppdateringResponseFromDeltaker(
                deltaker.copy(status = nyStatus),
                feilkode = DeltakerOppdateringFeilkode.UKJENT,
            ),
        )

        val resultatFraAmtDeltaker = tiltakskoordinatorService.endreDeltakere(
            listOf(deltaker.id),
            EndringFraTiltakskoordinator.SettPaaVenteliste,
            navAnsatt.navIdent,
        )
        val resultDeltaker = resultatFraAmtDeltaker.first()
        resultatFraAmtDeltaker.size shouldBe 1
        resultatFraAmtDeltaker.first().feilkode shouldBe DeltakerOppdateringFeilkode.UKJENT

        resultDeltaker.status.id shouldNotBe deltaker.status.id
        resultDeltaker.status.trimMss().copy(id = nyStatus.id) shouldBe nyStatus.trimMss()
    }
}

fun LocalDateTime.atStartOfDay(): LocalDateTime = this.toLocalDate().atStartOfDay()

fun DeltakerStatus.trimMss() = this.copy(
    opprettet = this.opprettet.atStartOfDay(),
    gyldigFra = this.gyldigFra.atStartOfDay(),
)
