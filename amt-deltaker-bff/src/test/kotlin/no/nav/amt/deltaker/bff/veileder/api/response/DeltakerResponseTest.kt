package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.response.DeltakelsesinnholdResponse.Companion.fulltInnhold
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import no.nav.amt.internapi.deltaker.toInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

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

    @Nested
    inner class FromDeltakerModel {
        @Test
        fun `manglende mengder bevares uten tomt erstatningsobjekt`() {
            // Arrange
            val model = ModelMapper.toDeltaker(lagDeltakerResponse()).copy(
                deltakelsesprosent = null,
                dagerPerUke = null,
                deltakelsesmengder = null,
            )

            // Act
            val response = DeltakerResponse.fromDeltakerModel(model)

            // Assert
            response.deltakelsesprosent shouldBe null
            response.dagerPerUke shouldBe null
            response.deltakelsesmengder shouldBe null
        }

        @Test
        fun `perioder uten prosent beholdes uavhengig av gjeldende mengde`() {
            listOf(3F, null).forEach { dager ->
                // Arrange
                val periode = DeltakelsesmengdeResponse(null, dager, LocalDate.of(2026, 12, 1))
                val model = ModelMapper.toDeltaker(lagDeltakerResponse()).copy(
                    deltakelsesprosent = 79.5F,
                    dagerPerUke = 4F,
                    deltakelsesmengder = DeltakelsesmengderResponse(periode, periode),
                )

                // Act
                val response = DeltakerResponse.fromDeltakerModel(model)

                // Assert
                response.deltakelsesprosent shouldBe 79.5F
                response.dagerPerUke shouldBe 4F
                response.deltakelsesmengder shouldBe model.deltakelsesmengder
            }
        }
    }
}
