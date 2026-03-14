package no.nav.tiltaksarrangor.melding.forslag

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.melding.forslag.request.ForlengDeltakelseRequest
import no.nav.tiltaksarrangor.testutils.DbTestDataUtils.shouldBeCloseTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime

class ForslagServiceTest(
	private val repository: ForslagRepository,
	private val forslagService: ForslagService,
) : IntegrationTest() {
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
			val erstattet = getProducedForslag(forslag.id)

			val status = erstattet.status as Forslag.Status.Erstattet
			status.erstattetMedForslagId shouldBe opprettetForslag.id
			status.erstattet shouldBeCloseTo opprettetForslag.opprettet

			assertProducedForslag(
				forslagId = opprettetForslag.id,
				endringstype = opprettetForslag.endring::class,
			)
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

			val tilbakekaltForslag = getProducedForslag(forslag.id)
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
