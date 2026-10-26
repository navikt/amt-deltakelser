package no.nav.amt.internapi.deltaker.response

import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue

class DeltakelsesinnholdResponseTest {
    @Test
    fun `skal bevare felter og rekkefolge med enkel konstruktor`() {
        val model = Deltakelsesinnhold(
            ledetekst = "Velg innhold",
            innhold = listOf(
                Innhold("Veiledning", "veiledning", true, "Individuell veiledning"),
                Innhold("Arbeidspraksis", "arbeidspraksis", false, null),
            ),
        )

        val response = DeltakelsesinnholdResponse(model)

        response shouldBe DeltakelsesinnholdResponse(
            ledetekst = "Velg innhold",
            innhold = listOf(
                DeltakelsesinnholdResponse.InnholdResponse("Veiledning", "veiledning", true, "Individuell veiledning"),
                DeltakelsesinnholdResponse.InnholdResponse("Arbeidspraksis", "arbeidspraksis", false, null),
            ),
        )
        val json = objectMapper.writeValueAsString(response)
        objectMapper.readTree(json) shouldBe objectMapper.readTree(
            """
            {
              "ledetekst": "Velg innhold",
              "innhold": [
                {"tekst": "Veiledning", "innholdskode": "veiledning", "valgt": true, "beskrivelse": "Individuell veiledning"},
                {"tekst": "Arbeidspraksis", "innholdskode": "arbeidspraksis", "valgt": false, "beskrivelse": null}
              ]
            }
            """.trimIndent(),
        )
        objectMapper.readValue<DeltakelsesinnholdResponse>(json) shouldBe response
    }

    @Test
    fun `skal legge til uvalgt innhold uten duplikater og sortere med annet sist`() {
        val model = Deltakelsesinnhold(
            ledetekst = "Velg innhold",
            innhold = listOf(
                Innhold("Veiledning", "veiledning", true, "Individuell veiledning"),
                Innhold.createFritekstInnhold("Tilpasset innhold"),
            ),
        )
        val tiltaksInnhold = listOf(
            annetInnholdselement,
            Innholdselement("Veiledning", "veiledning"),
            Innholdselement("Arbeidspraksis", "arbeidspraksis"),
        )

        val response = DeltakelsesinnholdResponse(model, tiltaksInnhold)

        response shouldBe DeltakelsesinnholdResponse(
            ledetekst = "Velg innhold",
            innhold = listOf(
                DeltakelsesinnholdResponse.InnholdResponse("Arbeidspraksis", "arbeidspraksis", false, null),
                DeltakelsesinnholdResponse.InnholdResponse("Veiledning", "veiledning", true, "Individuell veiledning"),
                DeltakelsesinnholdResponse.InnholdResponse("Annet", "annet", true, "Tilpasset innhold"),
            ),
        )
    }

    @Test
    fun `skal bevare valgt innhold som ikke lenger finnes i tiltaket`() {
        val model = Deltakelsesinnhold(
            ledetekst = null,
            innhold = listOf(Innhold("Tidligere innhold", "tidligere", true, "Beholdes")),
        )

        DeltakelsesinnholdResponse(model, emptyList()) shouldBe DeltakelsesinnholdResponse(model)
    }

    @Test
    fun `skal bevare null ledetekst og tomt innhold`() {
        val model = Deltakelsesinnhold(ledetekst = null, innhold = emptyList())
        val response = DeltakelsesinnholdResponse(model)

        response shouldBe DeltakelsesinnholdResponse(ledetekst = null, innhold = emptyList())
        DeltakelsesinnholdResponse(model, emptyList()) shouldBe response
        objectMapper.readTree(objectMapper.writeValueAsString(response)) shouldBe
            objectMapper.readTree("""{"ledetekst": null, "innhold": []}""")
    }
}
