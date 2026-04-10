package no.nav.amt.lib.models.deltaker

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeltakelsesinnholdTest {
    @Test
    fun `getAnnetFritekstBeskrivelse - har valgt annet med beskrivelse - returnerer beskrivelse`() {
        val deltakelsesinnhold = Deltakelsesinnhold(
            ledetekst = "Ledetekst",
            innhold = listOf(
                Innhold(tekst = "Arbeidspraksis", innholdskode = "arbeidspraksis", valgt = true, beskrivelse = null),
                Innhold(tekst = "Annet", innholdskode = "annet", valgt = true, beskrivelse = "Min fritekst"),
            ),
        )

        deltakelsesinnhold.getAnnetFritekstBeskrivelse() shouldBe "Min fritekst"
    }

    @Test
    fun `getAnnetFritekstBeskrivelse - har valgt annet uten beskrivelse - returnerer null`() {
        val deltakelsesinnhold = Deltakelsesinnhold(
            ledetekst = "Ledetekst",
            innhold = listOf(
                Innhold(tekst = "Annet", innholdskode = "annet", valgt = true, beskrivelse = null),
            ),
        )

        deltakelsesinnhold.getAnnetFritekstBeskrivelse() shouldBe null
    }

    @Test
    fun `getAnnetFritekstBeskrivelse - har valgt annet med blank beskrivelse - returnerer null`() {
        val deltakelsesinnhold = Deltakelsesinnhold(
            ledetekst = "Ledetekst",
            innhold = listOf(
                Innhold(tekst = "Annet", innholdskode = "annet", valgt = true, beskrivelse = "   "),
            ),
        )

        deltakelsesinnhold.getAnnetFritekstBeskrivelse() shouldBe null
    }

    @Test
    fun `getAnnetFritekstBeskrivelse - har valgt annet med tom beskrivelse - returnerer null`() {
        val deltakelsesinnhold = Deltakelsesinnhold(
            ledetekst = "Ledetekst",
            innhold = listOf(
                Innhold(tekst = "Annet", innholdskode = "annet", valgt = true, beskrivelse = ""),
            ),
        )

        deltakelsesinnhold.getAnnetFritekstBeskrivelse() shouldBe null
    }

    @Test
    fun `getAnnetFritekstBeskrivelse - annet er ikke valgt - returnerer null`() {
        val deltakelsesinnhold = Deltakelsesinnhold(
            ledetekst = "Ledetekst",
            innhold = listOf(
                Innhold(tekst = "Arbeidspraksis", innholdskode = "arbeidspraksis", valgt = true, beskrivelse = null),
                Innhold(tekst = "Annet", innholdskode = "annet", valgt = false, beskrivelse = "Skal ikke returneres"),
            ),
        )

        deltakelsesinnhold.getAnnetFritekstBeskrivelse() shouldBe null
    }

    @Test
    fun `getAnnetFritekstBeskrivelse - ingen annet-element finnes - returnerer null`() {
        val deltakelsesinnhold = Deltakelsesinnhold(
            ledetekst = "Ledetekst",
            innhold = listOf(
                Innhold(tekst = "Arbeidspraksis", innholdskode = "arbeidspraksis", valgt = true, beskrivelse = null),
            ),
        )

        deltakelsesinnhold.getAnnetFritekstBeskrivelse() shouldBe null
    }

    @Test
    fun `getAnnetFritekstBeskrivelse - tom innholdsliste - returnerer null`() {
        val deltakelsesinnhold = Deltakelsesinnhold(
            ledetekst = "Ledetekst",
            innhold = emptyList(),
        )

        deltakelsesinnhold.getAnnetFritekstBeskrivelse() shouldBe null
    }

    @Test
    fun `getAnnetFritekstBeskrivelse - flere valgte elementer inkludert annet - returnerer kun annet-beskrivelse`() {
        val deltakelsesinnhold = Deltakelsesinnhold(
            ledetekst = "Ledetekst",
            innhold = listOf(
                Innhold(tekst = "Arbeidspraksis", innholdskode = "arbeidspraksis", valgt = true, beskrivelse = null),
                Innhold(tekst = "Karriereveiledning", innholdskode = "karriereveiledning", valgt = true, beskrivelse = null),
                Innhold(tekst = "Annet", innholdskode = "annet", valgt = true, beskrivelse = "Spesiell tilrettelegging"),
            ),
        )

        deltakelsesinnhold.getAnnetFritekstBeskrivelse() shouldBe "Spesiell tilrettelegging"
    }
}
