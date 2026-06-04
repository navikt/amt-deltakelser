package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.util.UUID.randomUUID

class UtflatetKodeverkMapperTest {
    @Test
    fun `tilUtflatetKodeverk - flater ut valgte utdanningsprogram`() {
        val valgtUtdanningsprogramId = randomUUID()
        val valgtLaerefagId = randomUUID()

        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = listOf(
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = valgtUtdanningsprogramId,
                            visningsnavn = "Helse- og oppvekstfag",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = randomUUID(),
                                visningsnavn = "Lærefag",
                                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = valgtLaerefagId,
                                        visningsnavn = "Helsearbeiderfaget",
                                    ),
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = randomUUID(),
                                        visningsnavn = "Barne- og ungdomsarbeiderfaget",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtUtdanningsprogramId, valgtLaerefagId),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk shouldBe DeltakerlisteResponse.UtflatetKodeverk(
            valgteKategoriseringer = setOf(
                DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                    representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
                    valg = mapOf(valgtUtdanningsprogramId to "Helse- og oppvekstfag"),
                ),
                DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                    representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                    valg = mapOf(valgtLaerefagId to "Helsearbeiderfaget"),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )
    }

    @Test
    fun `tilUtflatetKodeverk - flater ut bransje og forerkort`() {
        val valgtBransjeId = randomUUID()
        val valgtForerkortId = randomUUID()
        val sertifiseringValg = setOf(SertifiseringValg(id = 1, navn = "Truckførerbevis"))

        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = randomUUID(),
                    visningsnavn = "Bransje",
                    pakrevd = true,
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                    alternativer = listOf(
                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                            id = valgtBransjeId,
                            visningsnavn = "Bygg og anlegg",
                        ),
                    ),
                ),
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = randomUUID(),
                    pakrevd = false,
                    visningsnavn = "Førerkortklasse",
                    representerer = OpplaringKategoriseringResponse.Representerer.FORERKORT,
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                    alternativer = listOf(
                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                            id = valgtForerkortId,
                            visningsnavn = "B - Personbil",
                        ),
                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                            id = randomUUID(),
                            visningsnavn = "C - Lastebil",
                        ),
                    ),
                ),
                OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok(
                    id = randomUUID(),
                    visningsnavn = "Sertifiseringer",
                    pakrevd = false,
                    representerer = OpplaringKategoriseringResponse.Representerer.SERTIFISERINGER,
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                    kilde = OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING,
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtBransjeId, valgtForerkortId),
            sertifiseringValg = sertifiseringValg,
        )

        utflatetKodeverk shouldBe DeltakerlisteResponse.UtflatetKodeverk(
            valgteKategoriseringer = setOf(
                DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    valg = mapOf(valgtBransjeId to "Bygg og anlegg"),
                ),
                DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                    representerer = OpplaringKategoriseringResponse.Representerer.FORERKORT,
                    valg = mapOf(valgtForerkortId to "B - Personbil"),
                ),
            ),
            valgteSertifiseringer = sertifiseringValg,
        )
    }

    @Test
    fun `tilUtflatetKodeverk - flater ut kurs`() {
        val valgtKurstypeId = randomUUID()

        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = randomUUID(),
                    pakrevd = true,
                    visningsnavn = "Kurstype",
                    representerer = OpplaringKategoriseringResponse.Representerer.KURSTYPE_ID,
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                    alternativer = listOf(
                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                            id = valgtKurstypeId,
                            visningsnavn = "Grunnleggende ferdigheter",
                        ),
                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                            id = randomUUID(),
                            visningsnavn = "Ikke valgt",
                        ),
                    ),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtKurstypeId),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk shouldBe DeltakerlisteResponse.UtflatetKodeverk(
            valgteKategoriseringer = setOf(
                DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                    representerer = OpplaringKategoriseringResponse.Representerer.KURSTYPE_ID,
                    valg = mapOf(valgtKurstypeId to "Grunnleggende ferdigheter"),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )
    }

    @Test
    fun `tilUtflatetKodeverk - tomt resultat nar ingenting er valgt`() {
        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = randomUUID(),
                    pakrevd = true,
                    visningsnavn = "Bransje",
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                    alternativer = listOf(
                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                            id = randomUUID(),
                            visningsnavn = "Bygg og anlegg",
                        ),
                    ),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = emptySet(),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk shouldBe DeltakerlisteResponse.UtflatetKodeverk(
            valgteKategoriseringer = emptySet(),
            valgteSertifiseringer = emptySet(),
        )
    }
}
