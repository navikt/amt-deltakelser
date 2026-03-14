package no.nav.tiltaksarrangor.repositories

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.tiltaksarrangor.RepositoryTestBase
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(classes = [DeltakerRepository::class])
class DeltakerRepositoryTest(
	private val sut: DeltakerRepository,
) : RepositoryTestBase() {
	@Test
	fun `oppdaterEnhetsnavnForDeltakere - nytt enhetsnavn - oppdaterer`() {
		with(DeltakerContext(applicationContext)) {
			val nyttEnhetsNavn = "Nytt Navn"
			sut.oppdaterEnhetsnavnForDeltakere(deltaker.navKontor!!, nyttEnhetsNavn)

			sut.getDeltaker(deltaker.id)!!.navKontor shouldBe nyttEnhetsNavn
		}
	}

	@Nested
	inner class GetDeltakereForDeltakerlisteTests {
		@Test
		fun `getDeltakereForDeltakerliste skal returnere deltaker`() {
			with(DeltakerContext(applicationContext)) {
				val deltakere = sut.getDeltakereForDeltakerliste(deltaker.deltakerlisteId)

				deltakere.size shouldBe 1
			}
		}

		@Test
		fun `getDeltakereForDeltakerliste med skjult_date, skal ikke returnere deltaker`() {
			with(DeltakerContext(applicationContext)) {
				deltakerRepository.insertOrUpdateDeltaker(
					deltaker.copy(
						skjultDato = LocalDateTime.now(),
					),
				)

				val deltakere = sut.getDeltakereForDeltakerliste(deltaker.deltakerlisteId)

				deltakere.shouldBeEmpty()
			}
		}

		@Test
		fun `getDeltakereForDeltakerliste med sluttdato 40 dager tilbake i tid, skal ikke returnere deltaker`() {
			with(DeltakerContext(applicationContext)) {
				deltakerRepository.insertOrUpdateDeltaker(
					deltaker.copy(
						sluttdato = LocalDate.now().minusDays(40),
					),
				)

				val deltakere = sut.getDeltakereForDeltakerliste(deltaker.deltakerlisteId)

				deltakere.shouldBeEmpty()
			}
		}

		@Test
		fun `getDeltakereForDeltakerliste med sluttdato 39 dager tilbake i tid, skal returnere deltaker`() {
			with(DeltakerContext(applicationContext)) {
				deltakerRepository.insertOrUpdateDeltaker(
					deltaker.copy(
						sluttdato = LocalDate.now().minusDays(39),
					),
				)

				val deltakere = sut.getDeltakereForDeltakerliste(deltaker.deltakerlisteId)

				deltakere.shouldNotBeEmpty()
			}
		}
	}
}
