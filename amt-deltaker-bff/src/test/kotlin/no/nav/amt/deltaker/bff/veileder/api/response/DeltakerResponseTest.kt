package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse.Companion.fulltInnhold
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.toInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import org.junit.jupiter.api.Test
import java.time.LocalDate
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse as InternDeltakelsesmengdeResponse

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
    fun `gyldigeDeltakelsesmengder - mapper alle perioder i riktig rekkefolge`() {
        val gyldigFra = LocalDate.now()
        val modeller = listOf(
            InternDeltakelsesmengdeResponse(
                deltakelsesprosent = 40F,
                dagerPerUke = 2F,
                gyldigFra = gyldigFra,
            ),
            InternDeltakelsesmengdeResponse(
                deltakelsesprosent = 60F,
                dagerPerUke = 3F,
                gyldigFra = gyldigFra.plusDays(7),
            ),
            InternDeltakelsesmengdeResponse(
                deltakelsesprosent = 80F,
                dagerPerUke = 4F,
                gyldigFra = gyldigFra.plusDays(14),
            ),
        )
        val deltaker = ModelMapper.toDeltaker(
            lagDeltakerResponse().copy(gyldigeDeltakelsesmengder = modeller),
        )

        val response = DeltakerResponse.fromDeltakerModel(deltaker)

        response.gyldigeDeltakelsesmengder shouldBe modeller.map(::DeltakelsesmengdeResponse)
    }
}
