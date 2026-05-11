package no.nav.amt.lib.ktor.clients.kodeverk

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.util.UUID

class KodeverkTest {
    @Test
    fun renderJsonTest() {
        val sut = KodeverkResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = listOf(
                KodeverkResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Førerkortklasser",
                    seleksjonstype = KodeverkResponse.Seleksjonstype.FLERVALG,
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Verdi(
                            id = UUID.randomUUID(),
                            visningsnavn = "B",
                        ),
                        KodeverkResponse.Alternativ.Verdi(
                            id = UUID.randomUUID(),
                            visningsnavn = "C",
                        ),
                    ),
                ),
                KodeverkResponse.Alternativ.Gruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Verdigruppe(
                            id = UUID.randomUUID(),
                            visningsnavn = "Frisør, blomster, interiør og ekspomeringsdesign",
                            seleksjonstype = KodeverkResponse.Seleksjonstype.ENKELTVALG,
                            alternativer = listOf(
                                KodeverkResponse.Alternativ.Verdi(
                                    id = UUID.randomUUID(),
                                    visningsnavn = "Blomsterdekoratørfaget",
                                ),
                                KodeverkResponse.Alternativ.Verdi(
                                    id = UUID.randomUUID(),
                                    visningsnavn = "Frisørfaget",
                                ),
                                KodeverkResponse.Alternativ.Verdi(
                                    id = UUID.randomUUID(),
                                    visningsnavn = "Interiør",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        println(objectMapper.writeValueAsString(sut))
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

        val result = objectMapper.readValue(json, KodeverkResponse::class.java)

        result.tiltakskode shouldBe Tiltakskode.ARBEIDSMARKEDSOPPLAERING
        result.alternativer.size shouldBe 3

        val verdigruppe = result.alternativer[0] as KodeverkResponse.Alternativ.Verdigruppe
        verdigruppe.visningsnavn shouldBe "Førerkortklasser"
        verdigruppe.representerer shouldBe "forerkortklasse"
        verdigruppe.seleksjonstype shouldBe KodeverkResponse.Seleksjonstype.FLERVALG
        verdigruppe.alternativer.size shouldBe 2
        verdigruppe.alternativer[0].visningsnavn shouldBe "B"

        val gruppe = result.alternativer[1] as KodeverkResponse.Alternativ.Gruppe
        gruppe.visningsnavn shouldBe "Utdanningsprogram"
        gruppe.alternativer.size shouldBe 1

        val nestedVerdigruppe = gruppe.alternativer[0] as KodeverkResponse.Alternativ.Verdigruppe
        nestedVerdigruppe.alternativer.size shouldBe 2

        val verdigruppeSok = result.alternativer[2] as KodeverkResponse.Alternativ.VerdigruppeSok
        verdigruppeSok.visningsnavn shouldBe "Sertifiseringer"
        verdigruppeSok.kilde shouldBe KodeverkResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING
    }
}
