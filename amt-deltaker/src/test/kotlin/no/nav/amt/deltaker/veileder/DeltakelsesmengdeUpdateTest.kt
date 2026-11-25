package no.nav.amt.deltaker.veileder

import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringService
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakelsesmengdeUpdateTest {
    @Test
    fun `lagret endring uten prosent oppdaterer deltaker uten fallback`() {
        listOf(3F, null).forEach { dager ->
            // Arrange
            val deltaker = lagDeltaker(deltakelsesprosent = 80F, dagerPerUke = 4F)
            val endring = lagDeltakerEndring(
                deltakerId = deltaker.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = null,
                    dagerPerUke = dager,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = null,
                ),
            )
            val repository = mockk<DeltakerEndringRepository>()
            val historikkService = mockk<DeltakerHistorikkService>()
            every { repository.upsert(endring, any()) } just runs
            every { historikkService.getForDeltaker(deltaker.id) } returns listOf(DeltakerHistorikk.Endring(endring))
            val service = DeltakerEndringService(
                deltakerEndringRepository = repository,
                navAnsattRepository = mockk(),
                navEnhetRepository = mockk(),
                distribuerEndringService = mockk(),
                forslagService = mockk(),
                deltakerHistorikkService = historikkService,
            )

            // Act
            val resultat = service.behandleLagretDeltakelsesmengde(endring, deltaker)

            // Assert
            val vellykketEndring = resultat.shouldBeSuccess()
            vellykketEndring.deltaker.deltakelsesprosent shouldBe null
            vellykketEndring.deltaker.dagerPerUke shouldBe dager
            verify(exactly = 1) { repository.upsert(endring, any()) }
        }
    }
}
