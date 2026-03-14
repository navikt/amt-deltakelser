package no.nav.tiltaksarrangor.repositories

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.tiltaksarrangor.RepositoryTestBase
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [DeltakerKontaktinfoRepository::class])
class DeltakerKontaktinfoRepositoryTest(
	private val deltakerKontaktinfoRepository: DeltakerKontaktinfoRepository,
) : RepositoryTestBase() {
	val kontaktinfo = Kontaktinformasjon(
		epost = "ny@epost",
		telefonnummer = "nyttTelefonnummer",
	)

	@Test
	fun `oppdaterKontaktinfo - en deltaker - oppdaterer`() {
		with(DeltakerContext(applicationContext)) {
			deltakerKontaktinfoRepository.oppdaterKontaktinformasjon(mapOf(deltaker.personident to kontaktinfo))

			assertKontaktinfoOppdatert(this, kontaktinfo)
		}
	}

	@Test
	fun `oppdaterKontaktinfo - flere deltakere - oppdaterer`() {
		val deltakerCtx1 = DeltakerContext(applicationContext)
		val deltakerCtx2 = DeltakerContext(applicationContext)
		deltakerKontaktinfoRepository.oppdaterKontaktinformasjon(
			mapOf(
				deltakerCtx1.deltaker.personident to kontaktinfo,
				deltakerCtx2.deltaker.personident to kontaktinfo,
			),
		)

		assertKontaktinfoOppdatert(deltakerCtx1, kontaktinfo)
		assertKontaktinfoOppdatert(deltakerCtx2, kontaktinfo)
	}

	@Test
	fun `getPersonerForOppdatering - distribuerer deltakere på time`() {
		with(
			DeltakerContext(applicationContext),
		) {
			(0..23).forEach { time ->
				medPersonident(time.toString())
				deltakerKontaktinfoRepository
					.getPersonerForOppdatering(time)
					.shouldNotBeNull()
					.shouldBe(listOf(deltaker.personident))
			}
		}
	}

	private fun assertKontaktinfoOppdatert(deltakerContext: DeltakerContext, kontaktinfo: Kontaktinformasjon) {
		val oppdatertDeltaker = deltakerContext.deltakerRepository
			.getDeltaker(deltakerContext.deltaker.id)
			.shouldNotBeNull()

		oppdatertDeltaker.telefonnummer shouldBe kontaktinfo.telefonnummer
		oppdatertDeltaker.epost shouldBe kontaktinfo.epost
	}
}
