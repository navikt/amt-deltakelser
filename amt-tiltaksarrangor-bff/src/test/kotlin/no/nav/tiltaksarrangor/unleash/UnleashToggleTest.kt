package no.nav.tiltaksarrangor.unleash

import io.getunleash.Unleash
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class UnleashToggleTest {
	private val unleashClient: Unleash = mockk(relaxed = true)
	private val sut = CommonUnleashToggle(unleashClient)

	@Nested
	inner class ErKometMasterForTiltakstype {
		@ParameterizedTest
		@EnumSource(
			value = Tiltakskode::class,
			names = [
				"ARBEIDSFORBEREDENDE_TRENING",
				"OPPFOLGING",
				"AVKLARING",
				"ARBEIDSRETTET_REHABILITERING",
				"DIGITALT_OPPFOLGINGSTILTAK",
				"VARIG_TILRETTELAGT_ARBEID_SKJERMET",
				"GRUPPE_ARBEIDSMARKEDSOPPLAERING",
				"JOBBKLUBB",
				"GRUPPE_FAG_OG_YRKESOPPLAERING",
			],
		)
		fun `returnerer true for tiltakstyper som Komet alltid er master for`(kode: Tiltakskode) {
			sut.erKometMasterForTiltakstype(kode) shouldBe true
		}
	}
}
