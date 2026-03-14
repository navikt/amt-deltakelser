package no.nav.tiltaksarrangor.consumer

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class GetNyDeltakerEndringFraTiltakskoordinatorTest {
	@Test
	fun `skal returnere null - ingen TildelPlass eller DelMedArrangor endringer`() {
		val foerrsteEndring = endringFraTiltakskoordinatorInTest
		val andreEndring = endringFraTiltakskoordinatorInTest.copy(
			endring = EndringFraTiltakskoordinator.Avslag(
				aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(type = EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT),
				begrunnelse = "z",
			),
		)

		val result = listOf(
			DeltakerHistorikk.EndringFraTiltakskoordinator(
				endringFraTiltakskoordinator = foerrsteEndring,
			),
			DeltakerHistorikk.EndringFraTiltakskoordinator(
				endringFraTiltakskoordinator = andreEndring,
			),
		).getNyDeltakerEndringFraTiltakskoordinator()

		result.shouldBeNull()
	}

	@Test
	fun `skal returnere nyeste TildelPlass endring`() {
		val foerrsteEndring = endringFraTiltakskoordinatorInTest.copy(endring = EndringFraTiltakskoordinator.TildelPlass)
		val andreEndring = foerrsteEndring.copy(endret = endringFraTiltakskoordinatorInTest.endret.plusDays(1))

		val result = listOf(
			DeltakerHistorikk.EndringFraTiltakskoordinator(
				endringFraTiltakskoordinator = foerrsteEndring,
			),
			DeltakerHistorikk.EndringFraTiltakskoordinator(
				endringFraTiltakskoordinator = andreEndring,
			),
		).getNyDeltakerEndringFraTiltakskoordinator()

		result.shouldNotBeNull()
		result.endringFraTiltakskoordinator shouldBe andreEndring
	}

	companion object {
		private val endringFraTiltakskoordinatorInTest = EndringFraTiltakskoordinator(
			id = UUID.randomUUID(),
			deltakerId = UUID.randomUUID(),
			endring = EndringFraTiltakskoordinator.SettPaaVenteliste,
			endret = LocalDateTime.now(),
			endretAv = UUID.randomUUID(),
			endretAvEnhet = UUID.randomUUID(),
		)
	}
}
