package no.nav.tiltaksarrangor.repositories

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.RepositoryTestBase
import no.nav.tiltaksarrangor.repositories.model.TiltakstypeDbo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest(classes = [TiltakstypeRepository::class])
class TiltakstypeRepositoryTest(
	private val sut: TiltakstypeRepository,
) : RepositoryTestBase() {
	@Nested
	inner class GetByTiltakskode {
		@Test
		fun `skal returnere null hvis tiltakstype ikke finnes`() {
			val inDb = sut.getByTiltakskode(tiltakstypeInTest.tiltakskode.name)

			inDb shouldBe null
		}

		@Test
		fun `skal returnere tiltakstype hvis tiltakstype finnes`() {
			sut.upsert(tiltakstypeInTest)

			val inDb = sut.getByTiltakskode(tiltakstypeInTest.tiltakskode.name)

			assertSoftly(inDb.shouldNotBeNull()) {
				id shouldBe tiltakstypeInTest.id
				navn shouldBe tiltakstypeInTest.navn
				tiltakskode shouldBe tiltakstypeInTest.tiltakskode
			}
		}
	}

	@Nested
	inner class Upsert {
		@Test
		fun `skal oppdatere eksisterende tiltakstype`() {
			sut.upsert(tiltakstypeInTest)

			val expectedTiltakstype = tiltakstypeInTest.copy(
				navn = "Oppdatert navn",
				tiltakskode = Tiltakskode.JOBBKLUBB,
			)
			sut.upsert(expectedTiltakstype)

			val inDb = sut.getByTiltakskode(expectedTiltakstype.tiltakskode.name)

			assertSoftly(inDb.shouldNotBeNull()) {
				navn shouldBe expectedTiltakstype.navn
				tiltakskode shouldBe expectedTiltakstype.tiltakskode
			}
		}
	}

	companion object {
		private val tiltakstypeInTest = TiltakstypeDbo(
			id = UUID.randomUUID(),
			navn = "~navn~",
			tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
		)
	}
}
