package no.nav.amt.lib.ktor.clients.kodeverk

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.util.UUID

class KodeverkTest {
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

        val firstVerdigruppe = result.alternativer.first()
        assertSoftly(firstVerdigruppe.shouldBeInstanceOf<KodeverkResponse.Alternativ.Verdigruppe>()) {
            visningsnavn shouldBe "Førerkortklasser"
            representerer shouldBe "forerkortklasse"
            seleksjonstype shouldBe KodeverkResponse.Seleksjonstype.FLERVALG
            alternativer.size shouldBe 2
            alternativer[0].visningsnavn shouldBe "B"
        }

        val gruppe = result.alternativer[1]
        assertSoftly(gruppe.shouldBeInstanceOf<KodeverkResponse.Alternativ.Gruppe>()) {
            visningsnavn shouldBe "Utdanningsprogram"
            alternativer.size shouldBe 1
        }

        val nestedVerdigruppe = gruppe.alternativer.first().shouldBeInstanceOf<KodeverkResponse.Alternativ.Verdigruppe>()
        nestedVerdigruppe.alternativer.size shouldBe 2

        val verdigruppeSok = result.alternativer[2]
        assertSoftly(verdigruppeSok.shouldBeInstanceOf<KodeverkResponse.Alternativ.VerdigruppeSok>()) {
            visningsnavn shouldBe "Sertifiseringer"
            representerer shouldBe "sertifisering"
            seleksjonstype shouldBe KodeverkResponse.Seleksjonstype.FLERVALG
            kilde shouldBe KodeverkResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING
        }
    }

    @Test
    fun `settValgt - setter valgt true for verdier i settet og false for andre`() {
        val verdiAId = UUID.randomUUID()
        val verdiBId = UUID.randomUUID()
        val verdiCId = UUID.randomUUID()

        val kodeverk = KodeverkResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = listOf(
                KodeverkResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Førerkortklasser",
                    seleksjonstype = KodeverkResponse.Seleksjonstype.FLERVALG,
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Verdi(id = verdiAId, visningsnavn = "B", valgt = true),
                        KodeverkResponse.Alternativ.Verdi(id = verdiBId, visningsnavn = "C", valgt = false),
                        KodeverkResponse.Alternativ.Verdi(id = verdiCId, visningsnavn = "D", valgt = true),
                    ),
                ),
            ),
        )

        val resultat = kodeverk.settValgt(setOf(verdiAId, verdiBId), emptySet())

        val verdier = (resultat.alternativer[0] as KodeverkResponse.Alternativ.Verdigruppe).alternativer
        verdier.first { it.id == verdiAId }.valgt shouldBe true
        verdier.first { it.id == verdiBId }.valgt shouldBe true
        verdier.first { it.id == verdiCId }.valgt shouldBe false
    }

    @Test
    fun `settValgt - traverserer nestede Gruppe-noder`() {
        val nestedVerdiId = UUID.randomUUID()

        val kodeverk = KodeverkResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = listOf(
                KodeverkResponse.Alternativ.Gruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Verdigruppe(
                            id = UUID.randomUUID(),
                            visningsnavn = "Frisør",
                            seleksjonstype = KodeverkResponse.Seleksjonstype.ENKELTVALG,
                            alternativer = listOf(
                                KodeverkResponse.Alternativ.Verdi(id = nestedVerdiId, visningsnavn = "Frisørfaget"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val resultat = kodeverk.settValgt(setOf(nestedVerdiId), emptySet())

        val gruppe = resultat.alternativer[0] as KodeverkResponse.Alternativ.Gruppe
        val verdigruppe = gruppe.alternativer[0] as KodeverkResponse.Alternativ.Verdigruppe
        verdigruppe.alternativer[0].valgt shouldBe true
    }

    @Test
    fun `settValgt - tom set setter alle valgt til false`() {
        val verdiId = UUID.randomUUID()

        val kodeverk = KodeverkResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = listOf(
                KodeverkResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Førerkortklasser",
                    seleksjonstype = KodeverkResponse.Seleksjonstype.FLERVALG,
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Verdi(id = verdiId, visningsnavn = "B", valgt = true),
                    ),
                ),
            ),
        )

        val resultat = kodeverk.settValgt(emptySet(), emptySet())

        val verdigruppe = resultat.alternativer[0] as KodeverkResponse.Alternativ.Verdigruppe
        verdigruppe.alternativer[0].valgt shouldBe false
    }

    @Test
    fun `settValgt - VerdigruppeSok forblir uendret`() {
        val sokId = UUID.randomUUID()
        val verdigruppeSok = KodeverkResponse.Alternativ.VerdigruppeSok(
            id = sokId,
            visningsnavn = "Sertifiseringer",
            seleksjonstype = KodeverkResponse.Seleksjonstype.FLERVALG,
            kilde = KodeverkResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING,
        )

        val kodeverk = KodeverkResponse(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = listOf(verdigruppeSok),
        )

        val resultat = kodeverk.settValgt(setOf(sokId, UUID.randomUUID()), emptySet())

        resultat.alternativer[0] shouldBe verdigruppeSok
    }
}
