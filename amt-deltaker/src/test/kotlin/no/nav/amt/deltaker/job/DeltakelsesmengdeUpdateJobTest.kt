package no.nav.amt.deltaker.job

import io.ktor.util.Attributes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringService
import no.nav.amt.lib.utils.leaderelection.LeaderElectionClient
import org.junit.jupiter.api.Test

class DeltakelsesmengdeUpdateJobTest {
    @Test
    fun `behandler videre batcher uten offset etter at forrige batch er behandlet`() {
        // Arrange
        val deltaker = lagDeltaker()
        val endringer = listOf(
            lagDeltakerEndring(deltakerId = deltaker.id),
            lagDeltakerEndring(deltakerId = deltaker.id),
        )
        val deltakerEndringRepository = mockk<DeltakerEndringRepository>()
        val deltakerRepository = mockk<DeltakerRepository>()
        val deltakerEndringService = mockk<DeltakerEndringService>()
        val deltakerService = mockk<DeltakerService>()

        // Simuler at køen blir kortere etter hver behandlede batch.
        every {
            deltakerEndringRepository.getUbehandletDeltakelsesmengder()
        } returnsMany listOf(
            listOf(endringer[0]),
            listOf(endringer[1]),
            emptyList(),
        )
        every { deltakerRepository.getOrThrow(deltaker.id) } returns deltaker
        // En ugyldig forretningsendring isolerer batchlogikken fra publisering.
        every {
            deltakerEndringService.behandleLagretDeltakelsesmengde(any(), deltaker)
        } returns Result.failure(IllegalStateException("Ugyldig endring"))

        val job = DeltakelsesmengdeUpdateJob(
            leaderElection = mockk<LeaderElectionClient>(),
            attributes = mockk<Attributes>(),
            deltakerEndringRepository = deltakerEndringRepository,
            deltakerEndringService = deltakerEndringService,
            deltakerRepository = deltakerRepository,
            deltakerService = deltakerService,
        )

        // Act
        job.behandleDeltakelsesmengder()

        // Assert
        verify(exactly = 3) { deltakerEndringRepository.getUbehandletDeltakelsesmengder() }
        verify(exactly = 2) {
            deltakerEndringService.behandleLagretDeltakelsesmengde(any(), deltaker)
        }
    }
}
