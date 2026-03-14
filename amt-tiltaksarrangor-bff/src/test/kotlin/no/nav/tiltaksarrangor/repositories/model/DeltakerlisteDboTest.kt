package no.nav.tiltaksarrangor.repositories.model

import io.kotest.matchers.shouldBe
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class DeltakerlisteDboTest {
	@Test
	fun `erTilgjengeligForArrangor - startdato er null - false`() {
		val deltakerlisteDbo = getDeltakerliste(UUID.randomUUID()).copy(
			startDato = null,
			tilgjengeligForArrangorFraOgMedDato = null,
		)

		deltakerlisteDbo.erTilgjengeligForArrangor() shouldBe false
	}

	@Test
	fun `erTilgjengeligForArrangor - tilgjengeligFom er null, startdato om 16 dager - false`() {
		val deltakerlisteDbo = getDeltakerliste(UUID.randomUUID()).copy(
			startDato = LocalDate.now().plusDays(16),
			tilgjengeligForArrangorFraOgMedDato = null,
		)

		deltakerlisteDbo.erTilgjengeligForArrangor() shouldBe false
	}

	@Test
	fun `erTilgjengeligForArrangor - tilgjengeligFom er null, startdato om 13 dager - true`() {
		val deltakerlisteDbo = getDeltakerliste(UUID.randomUUID()).copy(
			startDato = LocalDate.now().plusDays(13),
			tilgjengeligForArrangorFraOgMedDato = null,
		)

		deltakerlisteDbo.erTilgjengeligForArrangor() shouldBe true
	}

	@Test
	fun `erTilgjengeligForArrangor - tilgjengeligFom om 2 dager, startdato om 13 dager - false`() {
		val deltakerlisteDbo = getDeltakerliste(UUID.randomUUID()).copy(
			startDato = LocalDate.now().plusDays(13),
			tilgjengeligForArrangorFraOgMedDato = LocalDate.now().plusDays(2),
		)

		deltakerlisteDbo.erTilgjengeligForArrangor() shouldBe false
	}

	@Test
	fun `erTilgjengeligForArrangor - tilgjengeligFom for 2 dager siden, startdato om 20 dager - true`() {
		val deltakerlisteDbo = getDeltakerliste(UUID.randomUUID()).copy(
			startDato = LocalDate.now().plusDays(20),
			tilgjengeligForArrangorFraOgMedDato = LocalDate.now().minusDays(2),
		)

		deltakerlisteDbo.erTilgjengeligForArrangor() shouldBe true
	}
}
