package no.nav.amt.deltaker.tiltaksarrangor.endring

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagEndringFraArrangor
import no.nav.amt.deltaker.utils.data.TestRepository
import org.junit.jupiter.api.Test

class EndringFraArrangorRepositoryTest : IntegrationTestWithDbBase() {
    @Test
    fun `insert - endring - blir deltakerhistorikk`() {
        val deltaker = lagDeltaker()
        TestRepository.insert(deltaker)
        val endring = lagEndringFraArrangor(deltakerId = deltaker.id)

        endringFraArrangorRepository.insert(endring)

        val lagretEndring = endringFraArrangorRepository.getForDeltaker(deltaker.id)
        lagretEndring shouldHaveSize 1
        lagretEndring.single().id shouldBe endring.id
        lagretEndring.single().endring shouldBe endring.endring
    }
}
