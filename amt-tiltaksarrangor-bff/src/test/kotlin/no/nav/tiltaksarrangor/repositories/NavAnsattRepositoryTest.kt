package no.nav.tiltaksarrangor.repositories

import io.kotest.matchers.shouldBe
import no.nav.tiltaksarrangor.RepositoryTestBase
import no.nav.tiltaksarrangor.testutils.getNavAnsatt
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [NavAnsattRepository::class])
class NavAnsattRepositoryTest(
	private val ansattRepository: NavAnsattRepository,
) : RepositoryTestBase() {
	@Test
	fun `upsert - ny ansatt - inserter`() {
		val ansatt = getNavAnsatt()
		ansattRepository.upsert(ansatt)
		val insertedAnsatt = ansattRepository.get(ansatt.id)
		insertedAnsatt shouldBe ansatt
	}

	@Test
	fun `upsert - endret ansatt - oppdaterer`() {
		val ansatt = getNavAnsatt()
		ansattRepository.upsert(ansatt)
		val nyEpost = "foo@bar.baz"
		ansattRepository.upsert(ansatt.copy(epost = "foo@bar.baz"))

		val insertedAnsatt = ansattRepository.get(ansatt.id)
		insertedAnsatt!!.epost shouldBe nyEpost
	}
}
