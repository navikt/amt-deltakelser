package no.nav.amt.deltaker.bff.tiltakskoordinator.extensions

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.annetInnholdselement
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TiltakskoordinatorsDeltakerExtensionsTest {
    @Nested
    inner class GetDeltakelsesinnholdAnnet {
        @Test
        fun `skal returnere null hvis harTilgangTilBruker er false`() {
            getDeltakelsesinnholdAnnet(false, GjennomforingPameldingType.TRENGER_GODKJENNING, null) shouldBe null
        }

        @Test
        fun `skal returnere null hvis pameldingstype er null`() {
            getDeltakelsesinnholdAnnet(true, null, null) shouldBe null
        }

        @Test
        fun `skal returnere null hvis pameldingstype er DIREKTE_VEDTAK`() {
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.DIREKTE_VEDTAK, null) shouldBe null
        }

        @Test
        fun `skal returnere null hvis deltakelsesinnhold er null`() {
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, null) shouldBe null
        }

        @Test
        fun `skal returnere null hvis deltakelsesinnhold er tomt`() {
            val deltakelsesinnhold = Deltakelsesinnhold(ledetekst = null, innhold = emptyList())
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe null
        }

        @Test
        fun `skal returnere null hvis annetInnholdselement ikke finnes`() {
            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(Innhold(innholdskode = "IKKE_ANNET", beskrivelse = "beskrivelse", valgt = true, tekst = "IKKE annet")),
            )
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe null
        }

        @Test
        fun `skal returnere beskrivelse hvis annetInnholdselement finnes`() {
            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(innholdskode = annetInnholdselement.innholdskode, beskrivelse = "beskrivelse", valgt = true, tekst = "Annet"),
                ),
            )

            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe "beskrivelse"
        }

        @Test
        fun `skal returnere null hvis beskrivelse er tom eller whitespace`() {
            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(innholdskode = annetInnholdselement.innholdskode, beskrivelse = "   ", valgt = true, tekst = "Annet"),
                ),
            )
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe null
        }

        @Test
        fun `skal returnere null hvis beskrivelse er null`() {
            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(innholdskode = annetInnholdselement.innholdskode, beskrivelse = null, valgt = true, tekst = "Annet"),
                ),
            )
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe null
        }

        @Test
        fun `skal returnere beskrivelse for riktig element selv om flere finnes`() {
            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(innholdskode = "IKKE_ANNET", beskrivelse = "feil", valgt = true, tekst = "Feil"),
                    Innhold(innholdskode = annetInnholdselement.innholdskode, beskrivelse = "riktig", valgt = true, tekst = "Annet"),
                ),
            )
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe "riktig"
        }
    }
}
