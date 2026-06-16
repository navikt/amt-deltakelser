package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.veileder.api.request.EndreInnholdRequest
import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.internapi.deltaker.request.toInnholdModel
import no.nav.amt.internapi.deltaker.toInnhold
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerRegistreringInnhold
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue

class InnholdDtoTest {
    @Test
    fun testFinnValgtInnhold() {
        val innholdselement = Innholdselement("Type", "type")
        val deltaker = TestData.lagDeltakerOld(
            deltakerliste = TestData.lagDeltakerliste(
                tiltakstype = TestData.lagTiltakstype(
                    innhold = lagDeltakerRegistreringInnhold(
                        innholdselementer = listOf(innholdselement, annetInnholdselement),
                    ),
                ),
            ),
        )

        val annetBeskrivelse = "annet må ha en beskrivelse"

        val valgtInnhold = listOf(
            InnholdsElementRequest(innholdselement.innholdskode, null),
            InnholdsElementRequest(annetInnholdselement.innholdskode, annetBeskrivelse),
        ).toInnholdModel(deltaker.deltakerliste.tiltak)
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

        val deltaker = TestData.lagDeltakerOld(
            deltakerliste = TestData.lagDeltakerliste(
                tiltakstype = TestData.lagTiltakstype(
                    innhold = deltakerlisteInnhold,
                ),
            ),
        )

        val valgtInnhold = innholdRequest.innhold.toInnholdModel(deltaker.deltakerliste.tiltak)

        valgtInnhold.size shouldBe 2
        valgtInnhold.find { it.innholdskode == "arbeidspraksis" } shouldBe Innhold("Arbeidspraksis", "arbeidspraksis", true, null)
        valgtInnhold.find { it.innholdskode == annetInnholdselement.innholdskode } shouldBe annetInnholdselement.toInnhold(true, "blabla")
    }
}
