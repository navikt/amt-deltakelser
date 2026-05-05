package no.nav.amt.deltaker.bff.innbygger

import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.DeltakerTestUtils.sammenlignVedtak
import no.nav.amt.deltaker.bff.innbygger.InnbyggerTestUtils.deltakerMedIkkeFattetVedtak
import no.nav.amt.deltaker.bff.innbygger.InnbyggerTestUtils.fattVedtak
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.utils.DeltakerTestUtils.sammenlignDeltakere
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.deltaker.bff.utils.toUtkastResponse
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class InnbyggerServiceTest {
    private val amtDeltakerClient: AmtDeltakerClient = mockk(relaxed = true)
    private val amtPersonServiceClient: AmtPersonServiceClient = mockk(relaxed = true)
    private val navEnhetService = NavEnhetService(
        repository = NavEnhetRepository(),
        amtPersonServiceClient = amtPersonServiceClient,
    )
    private val deltakerService = DeltakerService(
        deltakerRepository = DeltakerRepository(),
        amtDeltakerClient = amtDeltakerClient,
        navEnhetService = navEnhetService,
        forslagRepository = mockk(),
    )

    private val paameldingClient: PaameldingClient = mockk(relaxed = true)
    private val innbyggerService = InnbyggerService(
        deltakerService = deltakerService,
        paameldingClient = paameldingClient,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() = clearAllMocks()

    @Test
    fun `godkjennUtkast - har feil status - feiler`() {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                innbyggerService.godkjennUtkast(deltaker)
            }
        }
    }

    @Test
    fun `godkjennUtkast - har riktig status - kaller amtDeltaker og oppdaterer deltaker`() = runTest {
        val deltaker = deltakerMedIkkeFattetVedtak()
        TestRepository.insert(deltaker)

        val deltakerMedFattetVedtak = deltaker.fattVedtak()

        coEvery { paameldingClient.innbyggerGodkjennUtkast(deltaker.id) } returns deltakerMedFattetVedtak.toUtkastResponse()

        val oppdatertDeltaker = innbyggerService.godkjennUtkast(deltaker)

        oppdatertDeltaker.ikkeFattetVedtak shouldBe null
        deltaker.ikkeFattetVedtak!!.id shouldBe oppdatertDeltaker.fattetVedtak!!.id

        sammenlignDeltakere(oppdatertDeltaker, deltakerMedFattetVedtak)
        sammenlignVedtak(oppdatertDeltaker.vedtaksinformasjon!!, deltakerMedFattetVedtak.vedtaksinformasjon!!)
    }
}
