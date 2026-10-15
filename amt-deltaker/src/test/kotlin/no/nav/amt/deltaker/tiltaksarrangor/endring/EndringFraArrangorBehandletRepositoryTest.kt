package no.nav.amt.deltaker.tiltaksarrangor.endring

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagEndringFraArrangor
import no.nav.amt.deltaker.utils.data.TestRepository
import org.junit.jupiter.api.Test

class EndringFraArrangorBehandletRepositoryTest : IntegrationTestWithDbBase() {
    @Test
    fun `exists - ukjent id - returnerer false`() {
        val endring = lagEndringFraArrangor()

        endringFraArrangorBehandletRepository.exists(endring.id) shouldBe false
    }

    @Test
    fun `markerSomBehandlet - id finnes kun en gang og blir ikke historikk`() {
        val deltaker = lagDeltaker()
        TestRepository.insert(deltaker)
        val endring = lagEndringFraArrangor(deltakerId = deltaker.id)

        endringFraArrangorBehandletRepository.markerSomBehandlet(endring.id, endring.deltakerId) shouldBe true
        endringFraArrangorBehandletRepository.markerSomBehandlet(endring.id, endring.deltakerId) shouldBe false

        endringFraArrangorBehandletRepository.exists(endring.id) shouldBe true
        endringFraArrangorRepository.getForDeltaker(deltaker.id) shouldBe emptyList()
    }
}
