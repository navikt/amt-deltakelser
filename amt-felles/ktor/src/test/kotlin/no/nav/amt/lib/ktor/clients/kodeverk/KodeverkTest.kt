package no.nav.amt.lib.ktor.clients.kodeverk

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class KodeverkTest {
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
                  "representerer": "utdanning",
                  "pakrevd": true,
                  "utdanninger": [
                    {
                      "id": "22222222-2222-2222-2222-222222222222",
                      "visningsnavn": "Helsearbeiderfag",
                      "larefag": {
                        "id": "33333333-3333-3333-3333-333333333333",
                        "visningsnavn": "Lærefag",
                        "pakrevd": false,
                        "representerer": "larefag",
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
        result.alternativer.size shouldBe 2
    }

    @Test
    fun `should deserialize response from Mulighetsrommet without type on Verdi`() {
        // JSON matching what the remote server (kotlinx.serialization) produces:
        // - "type" on Container subtypes (Verdigruppe, Gruppe, VerdigruppeSok)
        // - NO "type" on Verdi objects inside Verdigruppe.alternativer
        val json =
            """
            {
              "tiltakskode": "ARBEIDSMARKEDSOPPLAERING",
              "alternativer": [
                {
                  "type": "Verdigruppe",
                  "id": "11111111-1111-1111-1111-111111111111",
                  "visningsnavn": "Førerkortklasser",
                  "representerer": "forerkortklasse",
                  "seleksjonstype": "FLERVALG",
                  "alternativer": [
                    { "id": "aaaa1111-1111-1111-1111-111111111111", "visningsnavn": "B" },
                    { "id": "aaaa2222-2222-2222-2222-222222222222", "visningsnavn": "C1" }
                  ]
                },
                {
                  "type": "Gruppe",
                  "id": "22222222-2222-2222-2222-222222222222",
                  "visningsnavn": "Utdanningsprogram",
                  "alternativer": [
                    {
                      "type": "Verdigruppe",
                      "id": "33333333-3333-3333-3333-333333333333",
                      "visningsnavn": "Frisør, blomster, interiør",
                      "representerer": "programomrade",
                      "seleksjonstype": "ENKELTVALG",
                      "alternativer": [
                        { "id": "bbbb1111-1111-1111-1111-111111111111", "visningsnavn": "Blomsterdekoratørfaget" },
                        { "id": "bbbb2222-2222-2222-2222-222222222222", "visningsnavn": "Frisørfaget" }
                      ]
                    }
                  ]
                },
                {
                  "type": "VerdigruppeSok",
                  "id": "44444444-4444-4444-4444-444444444444",
                  "visningsnavn": "Sertifiseringer",
                  "representerer": "sertifisering",
                  "seleksjonstype": "FLERVALG",
                  "kilde": "JANZZ_SERTIFISERING"
                }
              ]
            }
            """.trimIndent()

        val result = objectMapper.readValue<OpplaringKategoriseringResponse>(json)

        result.tiltakskode shouldBe Tiltakskode.ARBEIDSMARKEDSOPPLAERING
        result.alternativer.size shouldBe 3

        val firstVerdigruppe = result.alternativer.first()
        assertSoftly(firstVerdigruppe.shouldBeInstanceOf<OpplaringKategoriseringResponse.Alternativ.Verdigruppe>()) {
            visningsnavn shouldBe "Førerkortklasser"
            representerer shouldBe "forerkortklasse"
            seleksjonstype shouldBe OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG
            alternativer.size shouldBe 2
            alternativer[0].visningsnavn shouldBe "B"
        }

        val gruppe = result.alternativer[1]
        assertSoftly(gruppe.shouldBeInstanceOf<OpplaringKategoriseringResponse.Alternativ.Gruppe>()) {
            visningsnavn shouldBe "Utdanningsprogram"
            alternativer.size shouldBe 1
        }

        val nestedVerdigruppe = gruppe.alternativer.first().shouldBeInstanceOf<OpplaringKategoriseringResponse.Alternativ.Verdigruppe>()
        nestedVerdigruppe.alternativer.size shouldBe 2

        val verdigruppeSok = result.alternativer[2]
        assertSoftly(verdigruppeSok.shouldBeInstanceOf<OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok>()) {
            visningsnavn shouldBe "Sertifiseringer"
            representerer shouldBe "sertifisering"
            seleksjonstype shouldBe OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG
            kilde shouldBe OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING
        }
    }

    @Test
    fun `settValgt - setter valgt true for verdier i settet og false for andre`() {
        val verdiAId = UUID.randomUUID()
        val verdiBId = UUID.randomUUID()
        val verdiCId = UUID.randomUUID()

        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Førerkortklasser",
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                    alternativer = listOf(
                        OpplaringKategoriseringResponse.Alternativ.Verdi(id = verdiAId, visningsnavn = "B", valgt = true),
                        OpplaringKategoriseringResponse.Alternativ.Verdi(id = verdiBId, visningsnavn = "C", valgt = false),
                        OpplaringKategoriseringResponse.Alternativ.Verdi(id = verdiCId, visningsnavn = "D", valgt = true),
                    ),
                ),
            ),
        )

        val resultat = kodeverk.settValgt(setOf(verdiAId, verdiBId), emptySet())

        val verdier = (resultat.alternativer[0] as OpplaringKategoriseringResponse.Alternativ.Verdigruppe).alternativer
        verdier.first { it.id == verdiAId }.valgt shouldBe true
        verdier.first { it.id == verdiBId }.valgt shouldBe true
        verdier.first { it.id == verdiCId }.valgt shouldBe false
    }

    @Test
    fun `settValgt - traverserer nestede Gruppe-noder`() {
        val nestedVerdiId = UUID.randomUUID()

        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Gruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    alternativer = listOf(
                        OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                            id = UUID.randomUUID(),
                            visningsnavn = "Frisør",
                            seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                            alternativer = listOf(
                                OpplaringKategoriseringResponse.Alternativ.Verdi(id = nestedVerdiId, visningsnavn = "Frisørfaget"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val resultat = kodeverk.settValgt(setOf(nestedVerdiId), emptySet())

        val gruppe = resultat.alternativer[0] as OpplaringKategoriseringResponse.Alternativ.Gruppe
        val verdigruppe = gruppe.alternativer[0] as OpplaringKategoriseringResponse.Alternativ.Verdigruppe
        verdigruppe.alternativer[0].valgt shouldBe true
    }

    @Test
    fun `settValgt - tom set setter alle valgt til false`() {
        val verdiId = UUID.randomUUID()

        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Førerkortklasser",
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                    alternativer = listOf(
                        OpplaringKategoriseringResponse.Alternativ.Verdi(id = verdiId, visningsnavn = "B", valgt = true),
                    ),
                ),
            ),
        )

        val resultat = kodeverk.settValgt(emptySet(), emptySet())

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
            kilde = OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING,
        )

        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = listOf(verdigruppeSok),
        )

        val resultat = kodeverk.settValgt(setOf(sokId, UUID.randomUUID()), emptySet())

        resultat.alternativer[0] shouldBe verdigruppeSok
    }
}
