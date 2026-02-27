package no.nav.amt.deltaker.bff.tiltakskoordinator

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.apiclients.DtoMappers.deltakerOppdateringResponseFromDeltaker
import no.nav.amt.deltaker.bff.apiclients.deltaker.AmtDeltakerClient
import no.nav.amt.deltaker.bff.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.bff.apiclients.tiltakskoordinator.TiltaksKoordinatorClient
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.tiltakskoordinator.extensions.toTiltakskoordinatorsDeltaker
import no.nav.amt.deltaker.bff.tiltakskoordinator.model.TiltakskoordinatorsDeltaker
import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.UlestHendelseService
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class TiltakskoordinatorServiceIntegrationTest {
    private val amtDeltakerClient = mockk<AmtDeltakerClient>()
    private val tiltaksKoordinatorClient = mockk<TiltaksKoordinatorClient>()

    private val navEnhetService = mockk<NavEnhetService>()
    private val navAnsattService = mockk<NavAnsattService>()
    private val vurderingService = mockk<VurderingService>()
    private val forslagRepository = mockk<ForslagRepository>()
    private val deltakerRepository = DeltakerRepository()
    private val deltakerService =
        DeltakerService(
            deltakerRepository = deltakerRepository,
            amtDeltakerClient = amtDeltakerClient,
            navEnhetService = navEnhetService,
            forslagRepository = forslagRepository,
        )
    private val amtDistribusjonClient = mockk<AmtDistribusjonClient>()
    private val ulestHendelseService = mockk<UlestHendelseService>()
    private val tiltakskoordinatorService =
        TiltakskoordinatorService(
            tiltaksKoordinatorClient,
            deltakerRepository,
            deltakerService,
            vurderingService,
            navEnhetService,
            navAnsattService,
            amtDistribusjonClient,
            forslagRepository,
            ulestHendelseService,
        )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `tildelPlass - returnerer og lagrer deltaker med ny status`(): Unit =
        runBlocking {
            val deltaker = TestData.lagDeltaker()
            val navEnhet = TestData.lagNavEnhet(id = deltaker.navBruker.navEnhetId!!)
            val navAnsatt = TestData.lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)

            TestRepository.insert(deltaker)
            every { vurderingService.getSisteVurderingForDeltaker(deltaker.id) } returns null
            every { navEnhetService.hentEnheter(listOf(navEnhet.id)) } returns mapOf(navEnhet.id to navEnhet)
            every { navAnsattService.hentAnsatte(listOf(navAnsatt.id)) } returns mapOf(navAnsatt.id to navAnsatt)
            coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
            every { forslagRepository.getForDeltakere(any()) } returns emptyList()
            every { forslagRepository.getForDeltaker(any()) } returns emptyList()
            every { ulestHendelseService.getUlesteHendelserForDeltaker(any()) } returns emptyList()

            val nyStatus =
                DeltakerStatus(
                    UUID.randomUUID(),
                    DeltakerStatus.Type.VENTER_PA_OPPSTART,
                    null,
                    LocalDateTime.now(),
                    null,
                    LocalDateTime.now(),
                )

            coEvery {
                tiltaksKoordinatorClient.tildelPlass(listOf(deltaker.id), navAnsatt.navIdent)
            } returns listOf(deltakerOppdateringResponseFromDeltaker(deltaker.copy(status = nyStatus)))

            val resultatFraAmtDeltaker =
                tiltakskoordinatorService.endreDeltakere(
                    listOf(deltaker.id),
                    EndringFraTiltakskoordinator.TildelPlass,
                    navAnsatt.navIdent,
                )
            val resultDeltaker = resultatFraAmtDeltaker.first()
            resultatFraAmtDeltaker.size shouldBe 1
            resultDeltaker.status.id shouldNotBe deltaker.status.id
            resultDeltaker.status.trimMss().copy(id = nyStatus.id) shouldBe nyStatus.trimMss()

            coEvery { navAnsattService.hentEllerOpprettNavAnsatt(navAnsatt.id) } returns navAnsatt
            every { navEnhetService.hentEnhet(navEnhet.id) } returns navEnhet

            val deltakerFraDb = tiltakskoordinatorService.getDeltaker(deltaker.id)
            deltakerFraDb shouldBeCloseTo
                deltaker
                    .copy(status = nyStatus)
                    .toTiltakskoordinatorsDeltaker(null, navEnhet, navAnsatt, null, false, emptyList(), emptyList())
        }

    @Test
    fun `settPaaVenteliste - returnerer og lagrer deltaker med ny status`(): Unit =
        runBlocking {
            val deltaker = TestData.lagDeltaker()
            val navEnhet = TestData.lagNavEnhet(id = deltaker.navBruker.navEnhetId!!)
            val navAnsatt = TestData.lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)

            TestRepository.insert(deltaker)
            every { vurderingService.getSisteVurderingForDeltaker(deltaker.id) } returns null
            every { navEnhetService.hentEnheter(listOf(navEnhet.id)) } returns mapOf(navEnhet.id to navEnhet)
            every { navAnsattService.hentAnsatte(listOf(navAnsatt.id)) } returns mapOf(navAnsatt.id to navAnsatt)
            coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
            every { forslagRepository.getForDeltakere(any()) } returns emptyList()
            every { forslagRepository.getForDeltaker(any()) } returns emptyList()
            every { ulestHendelseService.getUlesteHendelserForDeltaker(any()) } returns emptyList()

            val nyStatus =
                DeltakerStatus(UUID.randomUUID(), DeltakerStatus.Type.VENTELISTE, null, LocalDateTime.now(), null, LocalDateTime.now())

            coEvery {
                tiltaksKoordinatorClient.settPaaVenteliste(listOf(deltaker.id), navAnsatt.navIdent)
            } returns listOf(deltakerOppdateringResponseFromDeltaker(deltaker.copy(status = nyStatus)))

            val resultatFraAmtDeltaker =
                tiltakskoordinatorService.endreDeltakere(
                    listOf(deltaker.id),
                    EndringFraTiltakskoordinator.SettPaaVenteliste,
                    navAnsatt.navIdent,
                )
            val resultDeltaker = resultatFraAmtDeltaker.first()
            resultatFraAmtDeltaker.size shouldBe 1
            resultDeltaker.status.id shouldNotBe deltaker.status.id
            resultDeltaker.status.trimMss().copy(id = nyStatus.id) shouldBe nyStatus.trimMss()

            coEvery { navAnsattService.hentEllerOpprettNavAnsatt(navAnsatt.id) } returns navAnsatt
            every { navEnhetService.hentEnhet(navEnhet.id) } returns navEnhet

            val deltakerFraDb = tiltakskoordinatorService.getDeltaker(deltaker.id)
            deltakerFraDb shouldBeCloseTo
                deltaker
                    .copy(status = nyStatus)
                    .toTiltakskoordinatorsDeltaker(null, navEnhet, navAnsatt, null, false, emptyList(), emptyList())
        }

    @Test
    fun `settPaaVenteliste - en deltaker feiler i amt-deltaker - returnerer deltaker med feilkode`(): Unit =
        runBlocking {
            val deltaker = TestData.lagDeltaker()
            val navEnhet = TestData.lagNavEnhet(id = deltaker.navBruker.navEnhetId!!)
            val navAnsatt = TestData.lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)

            TestRepository.insert(deltaker)
            every { vurderingService.getSisteVurderingForDeltaker(deltaker.id) } returns null
            every { navEnhetService.hentEnheter(listOf(navEnhet.id)) } returns mapOf(navEnhet.id to navEnhet)
            every { navAnsattService.hentAnsatte(listOf(navAnsatt.id)) } returns mapOf(navAnsatt.id to navAnsatt)
            every { forslagRepository.getForDeltakere(any()) } returns emptyList()
            every { forslagRepository.getForDeltaker(any()) } returns emptyList()
            every { ulestHendelseService.getUlesteHendelserForDeltaker(any()) } returns emptyList()

            val nyStatus =
                DeltakerStatus(UUID.randomUUID(), DeltakerStatus.Type.VENTELISTE, null, LocalDateTime.now(), null, LocalDateTime.now())

            coEvery {
                tiltaksKoordinatorClient.settPaaVenteliste(listOf(deltaker.id), navAnsatt.navIdent)
            } returns
                listOf(
                    deltakerOppdateringResponseFromDeltaker(
                        deltaker.copy(status = nyStatus),
                        feilkode = DeltakerOppdateringFeilkode.UKJENT,
                    ),
                )

            val resultatFraAmtDeltaker =
                tiltakskoordinatorService.endreDeltakere(
                    listOf(deltaker.id),
                    EndringFraTiltakskoordinator.SettPaaVenteliste,
                    navAnsatt.navIdent,
                )
            val resultDeltaker = resultatFraAmtDeltaker.first()
            resultatFraAmtDeltaker.size shouldBe 1
            resultatFraAmtDeltaker.first().feilkode shouldBe DeltakerOppdateringFeilkode.UKJENT

            resultDeltaker.status.id shouldNotBe deltaker.status.id
            resultDeltaker.status.trimMss().copy(id = nyStatus.id) shouldBe nyStatus.trimMss()

            coEvery { navAnsattService.hentEllerOpprettNavAnsatt(navAnsatt.id) } returns navAnsatt
            every { navEnhetService.hentEnhet(navEnhet.id) } returns navEnhet

            val deltakerFraDb = tiltakskoordinatorService.getDeltaker(deltaker.id)
            deltakerFraDb shouldBeCloseTo
                deltaker
                    .copy(status = nyStatus)
                    .toTiltakskoordinatorsDeltaker(null, navEnhet, navAnsatt, null, false, emptyList(), emptyList())
        }
}

fun LocalDateTime.atStartOfDay(): LocalDateTime = this.toLocalDate().atStartOfDay()

fun DeltakerStatus.trimMss() =
    this.copy(
        opprettet = this.opprettet.atStartOfDay(),
        gyldigFra = this.gyldigFra.atStartOfDay(),
    )

infix fun TiltakskoordinatorsDeltaker.shouldBeCloseTo(expected: TiltakskoordinatorsDeltaker?) {
    this.copy(
        status =
            status.trimMss().copy(
                id = expected!!.status.id,
            ),
    ) shouldBe
        expected.copy(
            status =
                expected.status.trimMss().copy(
                    id = expected.status.id,
                ),
        )
}
