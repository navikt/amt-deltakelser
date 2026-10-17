package no.nav.amt.deltaker.tiltaksarrangor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagService
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArrangorMeldingConsumerTest {
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val forslagRepository = mockk<ForslagRepository>()
    private val vurderingRepository = mockk<VurderingRepository>(relaxed = true)

    private val consumer = ArrangorMeldingConsumer(
        endringFraArrangorService = mockk<EndringFraArrangorService>(),
        forslagRepository = forslagRepository,
        forslagService = mockk<ForslagService>(),
        deltakerRepository = deltakerRepository,
        vurderingRepository = vurderingRepository,
        deltakerProducerService = mockk<DeltakerProducerService>(relaxed = true),
    )

    @Test
    fun `ignorerer vurdering naar deltaker ikke finnes`() {
        // Arrange
        val vurdering = vurdering()
        every { forslagRepository.kanLagres(vurdering.deltakerId) } returns false
        every { deltakerRepository.get(vurdering.deltakerId) } returns
            Result.failure(NoSuchElementException("Fant ikke deltaker"))

        // Act
        consumer.consume(vurdering.id, objectMapper.writeValueAsString(vurdering))

        // Assert
        verify(exactly = 0) { vurderingRepository.upsert(any()) }
    }

    @Test
    fun `kaster videre databasefeil ved oppslag av deltaker`() {
        // Arrange
        val vurdering = vurdering()
        val databaseFeil = IllegalStateException("Database er utilgjengelig")
        every { forslagRepository.kanLagres(vurdering.deltakerId) } returns false
        every { deltakerRepository.get(vurdering.deltakerId) } returns Result.failure(databaseFeil)

        // Act
        val kastetFeil = shouldThrow<IllegalStateException> {
            consumer.consume(vurdering.id, objectMapper.writeValueAsString(vurdering))
        }

        // Assert
        kastetFeil.message shouldBe databaseFeil.message
        verify(exactly = 0) { vurderingRepository.upsert(any()) }
    }

    private fun vurdering() = Vurdering(
        id = UUID.randomUUID(),
        deltakerId = UUID.randomUUID(),
        opprettetAvArrangorAnsattId = UUID.randomUUID(),
        opprettet = LocalDateTime.now(),
        vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
        begrunnelse = null,
    )
}
