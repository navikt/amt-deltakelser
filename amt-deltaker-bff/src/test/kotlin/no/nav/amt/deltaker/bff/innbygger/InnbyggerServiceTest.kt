package no.nav.amt.deltaker.bff.innbygger

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.DeltakerTestUtils.sammenlignVedtak
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.utils.DeltakerTestUtils.sammenlignDeltakere
import no.nav.amt.deltaker.bff.utils.MockResponseHandler
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.deltaker.bff.utils.mockAmtDeltakerClient
import no.nav.amt.deltaker.bff.utils.mockAmtPersonServiceClient
import no.nav.amt.deltaker.bff.utils.mockPaameldingClient
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class InnbyggerServiceTest {
    private val amtDeltakerClient = mockAmtDeltakerClient()
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClient())
    private val deltakerService = DeltakerService(DeltakerRepository(), amtDeltakerClient, navEnhetService, mockk())
    private val innbyggerService = InnbyggerService(deltakerService, mockPaameldingClient())

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `godkjennUtkast - har feil status - feiler`() {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                innbyggerService.godkjennUtkast(deltaker)
            }
        }
    }

    @Test
    fun `godkjennUtkast - har riktig status - kaller amtDeltaker og oppdaterer deltaker`() = runTest {
        val deltaker = deltakerMedIkkeFattetVedtak()
        TestRepository.insert(deltaker)

        val deltakerMedFattetVedtak = deltaker.fattVedtak()

        MockResponseHandler.addInnbyggerGodkjennUtkastResponse(deltakerMedFattetVedtak)

        val oppdatertDeltaker = innbyggerService.godkjennUtkast(deltaker)

        oppdatertDeltaker.ikkeFattetVedtak shouldBe null
        deltaker.ikkeFattetVedtak!!.id shouldBe oppdatertDeltaker.fattetVedtak!!.id

        sammenlignDeltakere(oppdatertDeltaker, deltakerMedFattetVedtak)
        sammenlignVedtak(oppdatertDeltaker.vedtaksinformasjon!!, deltakerMedFattetVedtak.vedtaksinformasjon!!)
    }
}
