package no.nav.amt.deltaker.bff.deltaker.api.model

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.annetInnholdselement
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.toInnhold
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test

class InnholdDtoTest {
    @Test
    fun testFinnValgtInnhold() {
        val innholdselement = Innholdselement("Type", "type")
        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                tiltakstype = TestData.lagTiltakstype(
                    innhold = TestData.lagDeltakerRegistreringInnhold(
                        innholdselementer = listOf(innholdselement, annetInnholdselement),
                    ),
                ),
            ),
        )

        val annetBeskrivelse = "annet må ha en beskrivelse"

        val valgtInnhold = listOf(
            InnholdRequest(innholdselement.innholdskode, null),
            InnholdRequest(annetInnholdselement.innholdskode, annetBeskrivelse),
        ).toInnholdModel(deltaker = deltaker)
        valgtInnhold shouldBe listOf(
            innholdselement.toInnhold(true),
            annetInnholdselement.toInnhold(true, annetBeskrivelse),
        )
    }

    @Test
    fun `finnValgtInnhold - annet - annet skal bli valgt`() {
        val innholdRequest = objectMapper.readValue<EndreInnholdRequest>(
            """    	
            {
              "innhold": [
                {
                  "innholdskode": "arbeidspraksis",
                  "beskrivelse": null
                },
                {
                  "innholdskode": "annet",
                  "beskrivelse": "blabla"
                }
              ]
            }
            """.trimIndent(),
        )

        val deltakerlisteInnhold = objectMapper.readValue<DeltakerRegistreringInnhold>(
            """
            {
              "ledetekst": "Arbeidsforberedende trening er et tilbud for deg som først ønsker å jobbe i et tilrettelagt arbeidsmiljø.",
              "innholdselementer": [
                {
                  "tekst": "Arbeidspraksis",
                  "innholdskode": "arbeidspraksis"
                },
                {
                  "tekst": "Karriereveiledning",
                  "innholdskode": "karriereveiledning"
                }
              ],
              "innholdselementerMedAnnet": [
                {
                  "tekst": "Arbeidspraksis",
                  "innholdskode": "arbeidspraksis"
                },
                {
                  "tekst": "Karriereveiledning",
                  "innholdskode": "karriereveiledning"
                },
                {
                  "tekst": "Annet",
                  "innholdskode": "annet"
                }
              ]
            }
            """.trimIndent(),
        )

        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                tiltakstype = TestData.lagTiltakstype(
                    innhold = deltakerlisteInnhold,
                ),
            ),
        )

        val valgtInnhold = innholdRequest.innhold.toInnholdModel(deltaker = deltaker)

        valgtInnhold.size shouldBe 2
        valgtInnhold.find { it.innholdskode == "arbeidspraksis" } shouldBe Innhold("Arbeidspraksis", "arbeidspraksis", true, null)
        valgtInnhold.find { it.innholdskode == annetInnholdselement.innholdskode } shouldBe annetInnholdselement.toInnhold(true, "blabla")
    }
}
