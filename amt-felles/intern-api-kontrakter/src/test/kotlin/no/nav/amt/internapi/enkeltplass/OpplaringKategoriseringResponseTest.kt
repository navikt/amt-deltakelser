package no.nav.amt.internapi.enkeltplass

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class OpplaringKategoriseringResponseTest {
    @Test
    fun `should deserialize response from Mulighetsrommet`() {
        val json =
            """
            {
              "tiltakskode": "ARBEIDSMARKEDSOPPLAERING",
              "alternativer": [
                {
                  "type": "UtdanningGruppe",
                  "id": "11111111-1111-1111-1111-111111111111",
                  "visningsnavn": "Helse og oppvekst",
                  "representerer": "UTDANNINGSPROGRAM_ID",
                  "pakrevd": true,
                  "utdanninger": [
                    {
                      "id": "22222222-2222-2222-2222-222222222222",
                      "visningsnavn": "Helsearbeiderfag",
                      "larefag": {
                        "id": "33333333-3333-3333-3333-333333333333",
                        "visningsnavn": "Lærefag",
                        "pakrevd": false,
                        "representerer": "LAREFAG",
                        "seleksjonstype": "FLERVALG",
                        "alternativer": [
                          {
                            "id": "44444444-4444-4444-4444-444444444444",
                            "visningsnavn": "Helsearbeider",
                            "valgt": false
                          },
                          {
                            "id": "55555555-5555-5555-5555-555555555555",
                            "visningsnavn": "Ambulansefag",
                            "valgt": true
                          }
                        ]
                      }
                    }
                  ]
                }
              ],
              "sertifiseringValg": []
            }
            """.trimIndent()

        val result = objectMapper.readValue<OpplaringKategoriseringResponse>(json)

        result.tiltakskode shouldBe Tiltakskode.ARBEIDSMARKEDSOPPLAERING
        result.alternativer.size shouldBe 1
    }

    @Nested
    inner class SettValgtTests {
        @Test
        fun `settValgt - setter valgt true for verdier i settet og false for andre`() {
            val verdiAId = UUID.randomUUID()
            val verdiBId = UUID.randomUUID()
            val verdiCId = UUID.randomUUID()

            val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                alternativer = listOf(
                    OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                        id = UUID.randomUUID(),
                        visningsnavn = "Førerkortklasser",
                        seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                        pakrevd = true,
                        representerer = OpplaringKategoriseringType.FORERKORT,
                        alternativer = listOf(
                            OpplaringKategoriseringResponse.Alternativ.Verdi(id = verdiAId, visningsnavn = "B", valgt = true),
                            OpplaringKategoriseringResponse.Alternativ.Verdi(id = verdiBId, visningsnavn = "C", valgt = false),
                            OpplaringKategoriseringResponse.Alternativ.Verdi(id = verdiCId, visningsnavn = "D", valgt = true),
                        ),
                    ),
                ),
            )

            val resultat = opplaringKategoriseringResponse.settValg(setOf(verdiAId, verdiBId), emptySet())

            val verdier = (resultat.alternativer[0] as OpplaringKategoriseringResponse.Alternativ.Verdigruppe).alternativer
            verdier.first { it.id == verdiAId }.valgt shouldBe true
            verdier.first { it.id == verdiBId }.valgt shouldBe true
            verdier.first { it.id == verdiCId }.valgt shouldBe false
        }

        @Test
        fun `settValgt - tom set setter alle valgt til false`() {
            val verdiId = UUID.randomUUID()

            val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                alternativer = listOf(
                    OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                        id = UUID.randomUUID(),
                        visningsnavn = "Førerkortklasser",
                        pakrevd = true,
                        representerer = OpplaringKategoriseringType.FORERKORT,
                        seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                        alternativer = listOf(
                            OpplaringKategoriseringResponse.Alternativ.Verdi(id = verdiId, visningsnavn = "B", valgt = true),
                        ),
                    ),
                ),
            )

            val resultat = opplaringKategoriseringResponse.settValg(emptySet(), emptySet())

            val verdigruppe = resultat.alternativer[0] as OpplaringKategoriseringResponse.Alternativ.Verdigruppe
            verdigruppe.alternativer[0].valgt shouldBe false
        }

        @Test
        fun `settValgt - VerdigruppeSok forblir uendret`() {
            val sokId = UUID.randomUUID()
            val verdigruppeSok = OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok(
                id = sokId,
                visningsnavn = "Sertifiseringer",
                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                pakrevd = true,
                representerer = OpplaringKategoriseringType.SERTIFISERINGER,
                kilde = OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING,
            )

            val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                alternativer = listOf(verdigruppeSok),
            )

            val resultat = opplaringKategoriseringResponse.settValg(setOf(sokId, UUID.randomUUID()), emptySet())

            resultat.alternativer[0] shouldBe verdigruppeSok
        }
    }
}
