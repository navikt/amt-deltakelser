package no.nav.tiltaksarrangor.melding.forslag

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.tiltaksarrangor.IntegrationTestBase
import no.nav.tiltaksarrangor.melding.MeldingProducer
import no.nav.tiltaksarrangor.melding.forslag.request.ForlengDeltakelseRequest
import no.nav.tiltaksarrangor.testutils.DbTestDataUtils.shouldBeCloseTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime

class ForslagServiceTest(
    private val repository: ForslagRepository,
    private val forslagService: ForslagService,
) : IntegrationTestBase() {
    @Test
    fun `opprettForslag - forlengelse - produserer og returnerer nytt forslag`() {
        with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
            val sluttdato = LocalDate.now().plusWeeks(42)
            val begrunnelse = "Fordi..."
            val request = ForlengDeltakelseRequest(sluttdato, begrunnelse)
            val opprettetForslag =
                forslagService.opprettForslag(request, koordinator, deltaker)

            opprettetForslag.deltakerId shouldBe deltaker.id
            opprettetForslag.opprettetAvArrangorAnsattId shouldBe koordinator.id
            opprettetForslag.status shouldBe Forslag.Status.VenterPaSvar
            opprettetForslag.endring shouldBe Forslag.ForlengDeltakelse(sluttdato)
            opprettetForslag.begrunnelse shouldBe begrunnelse
            opprettetForslag.opprettet shouldBeCloseTo LocalDateTime.now()

            assertProducedForslag(
                jdbcTemplate = jdbcTemplate,
                forslagId = opprettetForslag.id,
                endringstype = opprettetForslag.endring::class,
            )
        }
    }

    @Test
    fun `opprettForslag - forlengelse, ventende forlengese finnes - erstatter gammelt forslag og returnerer nytt forslag`() {
        with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
            upsertForslag()

            val sluttdato = LocalDate.now().plusWeeks(42)
            val begrunnelse = "Fordi..."
            val request = ForlengDeltakelseRequest(sluttdato, begrunnelse)
            val opprettetForslag =
                forslagService.opprettForslag(request, koordinator, deltaker)

            opprettetForslag.status shouldBe Forslag.Status.VenterPaSvar
            opprettetForslag.opprettet shouldBeCloseTo LocalDateTime.now()

            repository.get(forslag.id).isFailure shouldBe true
            val erstattet = getProducedForslag(jdbcTemplate, forslag.id)

            val status = erstattet.status as Forslag.Status.Erstattet
            status.erstattetMedForslagId shouldBe opprettetForslag.id
            status.erstattet shouldBeCloseTo opprettetForslag.opprettet

            assertProducedForslag(
                jdbcTemplate = jdbcTemplate,
                forslagId = opprettetForslag.id,
                endringstype = opprettetForslag.endring::class,
            )
        }
    }

    @Test
    fun `opprettForslag - databasefeil ved oppslag av ventende forslag - kaster videre`() {
        // Arrange
        with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
            val databaseFeil = IllegalStateException("Database er utilgjengelig")
            val mockRepository = mockk<ForslagRepository>()
            val mockMeldingProducer = mockk<MeldingProducer>()
            every { mockRepository.getVentende(any()) } returns Result.failure(databaseFeil)
            val service = ForslagService(mockRepository, mockMeldingProducer)
            val request = ForlengDeltakelseRequest(LocalDate.now().plusWeeks(42), "Fordi...")

            // Act
            val kastetFeil = shouldThrow<IllegalStateException> {
                service.opprettForslag(request, koordinator, deltaker)
            }

            // Assert
            kastetFeil.message shouldBe databaseFeil.message
            verify(exactly = 0) { mockRepository.upsert(any()) }
            verify(exactly = 0) { mockMeldingProducer.produce(any<Forslag>()) }
        }
    }

    @Test
    fun `getAktiveForslag - filtrerer bort forslag uten riktig status`() {
        with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
            upsertForslag()
            medInaktiveForslag()

            val aktiveForslag = forslagService.getAktiveForslag(deltaker.id)
            aktiveForslag.size shouldBe 1
            aktiveForslag.first().status shouldBe Forslag.Status.VenterPaSvar
        }
    }

    @Test
    fun `tilbakekall - forslag er aktivt - sletter og produserer forslag med riktig status`() {
        with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
            upsertForslag()

            forslagService.tilbakekall(forslag.id, koordinator)

            val tilbakekaltForslag = getProducedForslag(jdbcTemplate, forslag.id)
            val status = tilbakekaltForslag.status as Forslag.Status.Tilbakekalt

            status.tilbakekalt shouldBeCloseTo LocalDateTime.now()
            status.tilbakekaltAvArrangorAnsattId shouldBe koordinator.id
        }
    }

    @Test
    fun `tilbakekall - forslag er ikke aktivt - feiler`() {
        with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
            setForslagGodkjent()
            upsertForslag()

            assertThrows<IllegalArgumentException> {
                forslagService.tilbakekall(forslag.id, koordinator)
            }
        }
    }
}
