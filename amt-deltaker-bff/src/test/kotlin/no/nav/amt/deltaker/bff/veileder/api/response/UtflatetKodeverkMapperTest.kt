package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.util.UUID

class UtflatetKodeverkMapperTest {
    @Test
    fun `tilUtflatetKodeverk - flater ut valgte utdanningsprogram`() {
        val valgtLaerefagId = UUID.randomUUID()
        val ikkeValgtLaerefagId = UUID.randomUUID()

        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = listOf(
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = UUID.randomUUID(),
                            visningsnavn = "Helse- og oppvekstfag",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = UUID.randomUUID(),
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
                                        id = ikkeValgtLaerefagId,
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
            kodeverkValg = setOf(valgtLaerefagId),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk shouldBe DeltakerlisteResponse.UtflatetKodeverk(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            tittel = "Helse- og oppvekstfag",
            valg = listOf("Helsearbeiderfaget"),
            valgteKodeverkIder = setOf(valgtLaerefagId),
            valgteSertifiseringer = emptySet(),
        )
    }

    @Test
    fun `tilUtflatetKodeverk - bruker bransjenavn som tittel for bransjer`() {
        val valgtBransjeId = UUID.randomUUID()
        val valgtForerkortId = UUID.randomUUID()
        val sertifiseringValg = setOf(SertifiseringValg(id = 1, navn = "Truckførerbevis"))

        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
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
                    ),
                ),
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
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
                            id = UUID.randomUUID(),
                            visningsnavn = "C - Lastebil",
                        ),
                    ),
                ),
                OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok(
                    id = UUID.randomUUID(),
                    visningsnavn = "Sertifiseringer",
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
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            tittel = "Bygg og anlegg",
            valg = listOf("B - Personbil", "Truckførerbevis"),
            valgteKodeverkIder = setOf(valgtBransjeId, valgtForerkortId),
            valgteSertifiseringer = sertifiseringValg,
        )
    }

    @Test
    fun `tilUtflatetKodeverk - bruker kurstype som tittel`() {
        val valgtKurstypeId = UUID.randomUUID()

        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    pakrevd = true,
                    visningsnavn = "Kurstype",
                    representerer = OpplaringKategoriseringResponse.Representerer.KURSTYPE_ID,
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                    alternativer = listOf(
                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                            id = valgtKurstypeId,
                            visningsnavn = "Grunnleggende ferdigheter",
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
            tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            tittel = "Grunnleggende ferdigheter",
            valg = emptyList(),
            valgteKodeverkIder = setOf(valgtKurstypeId),
            valgteSertifiseringer = emptySet(),
        )
    }

    @Test
    fun `tilUtflatetKodeverk - tittel er null når ingen bransje er valgt`() {
        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    pakrevd = true,
                    visningsnavn = "Bransje",
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                    alternativer = listOf(
                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                            id = UUID.randomUUID(),
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
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            tittel = null,
            valg = emptyList(),
            valgteKodeverkIder = emptySet(),
            valgteSertifiseringer = emptySet(),
        )
    }
}
