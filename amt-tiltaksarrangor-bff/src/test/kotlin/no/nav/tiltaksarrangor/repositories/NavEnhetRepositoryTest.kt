package no.nav.tiltaksarrangor.repositories

import io.kotest.matchers.shouldBe
import no.nav.tiltaksarrangor.RepositoryTestBase
import no.nav.tiltaksarrangor.testutils.getNavEnhet
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [NavEnhetRepository::class])
class NavEnhetRepositoryTest(
	private val enhetRepository: NavEnhetRepository,
) : RepositoryTestBase() {
	@Test
	fun `upsert - ny enhet - inserter`() {
		val enhet = getNavEnhet()
		enhetRepository.upsert(enhet)
		val insertedEnhet = enhetRepository.get(enhet.id)
		insertedEnhet!!.enhetsnummer shouldBe enhet.enhetsnummer
	}

	@Test
	fun `upsert - endret enhet - oppdaterer`() {
		val enhet = getNavEnhet()
		enhetRepository.upsert(enhet)

		val navn = "Nytt Navn"
		enhetRepository.upsert(enhet.copy(navn = navn))

		enhetRepository.get(enhet.id)!!.navn shouldBe navn
	}
}
