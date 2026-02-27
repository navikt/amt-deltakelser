package no.nav.amt.deltaker.bff.deltaker.api.model

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.annetInnholdselement
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.toInnhold
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import org.junit.jupiter.api.Test
import java.util.UUID

class DeltakerResponseTest {
    private val innholdselementer = listOf(
        annetInnholdselement,
        Innholdselement("Innhold 4", "innhold-4"),
        Innholdselement("Innhold 3", "innhold-3"),
        Innholdselement("Innhold 2", "innhold-2"),
        Innholdselement("Innhold 1", "innhold-1"),
    )

    @Test
    fun `fulltInnhold - ingen innhold er valgt - returner liste med innhold som ikke er valgt og riktig sortert`() {
        val innhold = fulltInnhold(emptyList(), innholdselementer)
        innhold.size shouldBe innholdselementer.size
        innhold.forEach { it.valgt shouldBe false }
        innhold.forEachIndexed { index, innholdelement ->
            if (index == (innhold.size - 1)) {
                innholdelement.tekst shouldBe annetInnholdselement.tekst
            } else {
                innholdelement.tekst shouldBe "Innhold ${index + 1}"
            }
        }
    }

    @Test
    fun `fulltInnhold - noe innhold er valgt - returner liste med innhold som er valgt og ikke er valgt`() {
        val valgtInnhold = listOf(
            innholdselementer.last().toInnhold(valgt = true),
            annetInnholdselement.toInnhold(valgt = true, beskrivelse = "fordi"),
        )

        val innhold = fulltInnhold(valgtInnhold, innholdselementer)
        innhold.size shouldBe innholdselementer.size
        innhold.forEach {
            when (it.innholdskode) {
                valgtInnhold[0].innholdskode -> it.valgt shouldBe true
                valgtInnhold[1].innholdskode -> {
                    it.valgt shouldBe true
                    it.beskrivelse shouldBe valgtInnhold[1].beskrivelse
                }

                else -> it.valgt shouldBe false
            }
        }
    }

    @Test
    fun `getArrangorNavn - har ikke overordnet arrangor - bruker arrangornavn i titlecase`() {
        val arrangor = Deltakerliste.Arrangor(
            arrangor = Arrangor(
                id = UUID.randomUUID(),
                navn = "DIN ARRANGØR AS",
                organisasjonsnummer = TestData.randomOrgnr(),
                overordnetArrangorId = null,
            ),
            overordnetArrangorNavn = null,
        )

        arrangor.getArrangorNavn() shouldBe "Din Arrangør AS"
    }

    @Test
    fun `getArrangorNavn - har overordnet arrangor - bruker overordnet arrangornavn i titlecase`() {
        val arrangor = Deltakerliste.Arrangor(
            arrangor = Arrangor(
                id = UUID.randomUUID(),
                navn = "DIN ARRANGØR AS",
                organisasjonsnummer = TestData.randomOrgnr(),
                overordnetArrangorId = UUID.randomUUID(),
            ),
            overordnetArrangorNavn = "TILTAK OG MULIGHETER",
        )

        arrangor.getArrangorNavn() shouldBe "Tiltak og Muligheter"
    }
}
