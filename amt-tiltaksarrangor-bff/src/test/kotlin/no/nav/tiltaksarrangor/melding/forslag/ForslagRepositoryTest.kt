package no.nav.tiltaksarrangor.melding.forslag

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.tiltaksarrangor.RepositoryTestBase
import no.nav.tiltaksarrangor.testutils.DbTestDataUtils.shouldBeCloseTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(classes = [ForslagRepository::class])
class ForslagRepositoryTest : RepositoryTestBase() {
	@Autowired
	private lateinit var repository: ForslagRepository

	@Test
	fun `upsert - nytt forslag - inserter`() {
		with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
			val insertedForslag = repository.upsert(forslag)

			insertedForslag.id shouldBe forslag.id
			insertedForslag.deltakerId shouldBe forslag.deltakerId
			insertedForslag.opprettetAvArrangorAnsattId shouldBe forslag.opprettetAvArrangorAnsattId
			insertedForslag.opprettet shouldBeCloseTo forslag.opprettet
			insertedForslag.endring shouldBe forslag.endring
			insertedForslag.status shouldBe forslag.status
		}
	}

	@Test
	fun `upsert - oppdatert forslag - oppdaterer`() {
		with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
			repository.upsert(forslag)

			val nyStatus = Forslag.Status.Godkjent(
				godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
				godkjent = LocalDateTime.now(),
			)

			val oppdatertForslag = forslag.copy(status = nyStatus)

			val oppdatertStatus = repository.upsert(oppdatertForslag).status as Forslag.Status.Godkjent

			oppdatertStatus.godkjent shouldBe nyStatus.godkjent
			oppdatertStatus.godkjentAv shouldBe nyStatus.godkjentAv
		}
	}

	@Test
	fun `delete - sletter forslag`() {
		with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
			setForslagGodkjent()
			upsertForslag()

			repository.delete(forslag.id) shouldBe 1
			repository.get(forslag.id).isFailure shouldBe true
		}
	}

	@Test
	fun `delete - forslag finnes ikke - returnerer antall slettede rader`() {
		repository.delete(UUID.randomUUID()) shouldBe 0
	}
}
