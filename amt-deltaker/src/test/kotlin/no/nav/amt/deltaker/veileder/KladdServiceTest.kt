package no.nav.amt.deltaker.veileder

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.deltaker.innbygger.NavBrukerService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.service.DeltakerService
import org.junit.jupiter.api.Test
import java.util.UUID

class KladdServiceTest {
    @Test
    fun `databasefeil ved oppslag av kladd kastes videre`() {
        // Arrange
        val deltakerId = UUID.randomUUID()
        val databaseFeil = IllegalStateException("Database er utilgjengelig")
        val deltakerRepository = mockk<DeltakerRepository>()
        every { deltakerRepository.get(deltakerId) } returns Result.failure(databaseFeil)
        val service = KladdService(
            deltakerRepository = deltakerRepository,
            deltakerService = mockk<DeltakerService>(),
            deltakerlisteRepository = mockk<DeltakerlisteRepository>(),
            navBrukerService = mockk<NavBrukerService>(),
        )

        // Act
        val kastetFeil = shouldThrow<IllegalStateException> {
            service.slettKladd(deltakerId)
        }

        // Assert
        kastetFeil.message shouldBe databaseFeil.message
        verify(exactly = 1) { deltakerRepository.get(deltakerId) }
    }
}
