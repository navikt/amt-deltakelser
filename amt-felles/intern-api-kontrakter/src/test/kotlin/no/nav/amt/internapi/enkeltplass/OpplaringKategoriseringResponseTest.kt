package no.nav.amt.internapi.enkeltplass

import io.kotest.matchers.shouldBe
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

            val kodeverk = OpplaringKategoriseringResponse(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                alternativer = listOf(
                    OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                        id = UUID.randomUUID(),
                        visningsnavn = "Førerkortklasser",
                        seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                        pakrevd = true,
                        representerer = OpplaringKategoriseringResponse.Representerer.FORERKORT,
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
        fun `settValgt - tom set setter alle valgt til false`() {
            val verdiId = UUID.randomUUID()

            val kodeverk = OpplaringKategoriseringResponse(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                alternativer = listOf(
                    OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                        id = UUID.randomUUID(),
                        visningsnavn = "Førerkortklasser",
                        pakrevd = true,
                        representerer = OpplaringKategoriseringResponse.Representerer.FORERKORT,
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
                pakrevd = true,
                representerer = OpplaringKategoriseringResponse.Representerer.SERTIFISERINGER,
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

    @Nested
    inner class GrupperKodeverkvalgPerRepresentererTests {
        @Test
        fun `grupperKodeverkvalgPerRepresenterer - returnerer tomt map ved ingen valgte ider`() {
            val verdiId = UUID.randomUUID()
            val kodeverk = OpplaringKategoriseringResponse(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                alternativer = listOf(
                    OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                        id = UUID.randomUUID(),
                        visningsnavn = "Bransje",
                        pakrevd = true,
                        representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                        seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                        alternativer = listOf(
                            OpplaringKategoriseringResponse.Alternativ.Verdi(
                                id = verdiId,
                                visningsnavn = "Bygg og anlegg",
                            ),
                        ),
                    ),
                ),
            )

            val resultat = kodeverk.grupperKodeverkvalgPerRepresenterer(emptySet())

            resultat shouldBe emptyMap()
        }

        @Test
        fun `grupperKodeverkvalgPerRepresenterer - grupperer valgte verdier per representerer`() {
            val valgtBransjeId = UUID.randomUUID()
            val ikkeValgtBransjeId = UUID.randomUUID()
            val valgtForerkortId = UUID.randomUUID()

            val kodeverk = OpplaringKategoriseringResponse(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                alternativer = listOf(
                    OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                        id = UUID.randomUUID(),
                        visningsnavn = "Bransje",
                        pakrevd = true,
                        representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                        seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                        alternativer = listOf(
                            OpplaringKategoriseringResponse.Alternativ.Verdi(
                                id = valgtBransjeId,
                                visningsnavn = "Bygg og anlegg",
                            ),
                            OpplaringKategoriseringResponse.Alternativ.Verdi(
                                id = ikkeValgtBransjeId,
                                visningsnavn = "Helse og omsorg",
                            ),
                        ),
                    ),
                    OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                        id = UUID.randomUUID(),
                        visningsnavn = "Førerkortklasse",
                        pakrevd = false,
                        representerer = OpplaringKategoriseringResponse.Representerer.FORERKORT,
                        seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                        alternativer = listOf(
                            OpplaringKategoriseringResponse.Alternativ.Verdi(
                                id = valgtForerkortId,
                                visningsnavn = "B",
                            ),
                        ),
                    ),
                    OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok(
                        id = UUID.randomUUID(),
                        visningsnavn = "Sertifiseringer",
                        pakrevd = false,
                        representerer = OpplaringKategoriseringResponse.Representerer.SERTIFISERINGER,
                        seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                        kilde = OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING,
                    ),
                ),
            )

            val resultat = kodeverk.grupperKodeverkvalgPerRepresenterer(setOf(valgtBransjeId, valgtForerkortId))

            resultat shouldBe mapOf(
                OpplaringKategoriseringResponse.Representerer.BRANSJE_ID to setOf(valgtBransjeId),
                OpplaringKategoriseringResponse.Representerer.FORERKORT to setOf(valgtForerkortId),
            )
        }

        @Test
        fun `grupperKodeverkvalgPerRepresenterer - inkluderer valgt utdanningsprogram og larefag`() {
            val valgtProgramId = UUID.randomUUID()
            val ikkeValgtProgramId = UUID.randomUUID()
            val valgtLarefagId = UUID.randomUUID()
            val ikkeValgtLarefagId = UUID.randomUUID()

            val kodeverk = OpplaringKategoriseringResponse(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                alternativer = listOf(
                    OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                        id = UUID.randomUUID(),
                        visningsnavn = "Utdanningsprogram",
                        representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
                        pakrevd = true,
                        utdanninger = listOf(
                            OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                                id = valgtProgramId,
                                visningsnavn = "Helse og oppvekst",
                                larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                    id = UUID.randomUUID(),
                                    visningsnavn = "Lærefag",
                                    pakrevd = true,
                                    representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                    alternativer = listOf(
                                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                                            id = valgtLarefagId,
                                            visningsnavn = "Helsearbeider",
                                        ),
                                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                                            id = ikkeValgtLarefagId,
                                            visningsnavn = "Ambulansefag",
                                        ),
                                    ),
                                ),
                            ),
                            OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                                id = ikkeValgtProgramId,
                                visningsnavn = "Bygg og anlegg",
                                larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                    id = UUID.randomUUID(),
                                    visningsnavn = "Lærefag",
                                    pakrevd = true,
                                    representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                    alternativer = emptyList(),
                                ),
                            ),
                        ),
                    ),
                ),
            )

            val resultat = kodeverk.grupperKodeverkvalgPerRepresenterer(setOf(valgtProgramId, valgtLarefagId))

            resultat shouldBe mapOf(
                OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID to setOf(valgtProgramId),
                OpplaringKategoriseringResponse.Representerer.LAREFAG to setOf(valgtLarefagId),
            )
        }
    }
}
