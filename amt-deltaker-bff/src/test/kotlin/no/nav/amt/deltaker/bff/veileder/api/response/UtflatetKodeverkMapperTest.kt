package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkResponse
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.util.UUID

class UtflatetKodeverkMapperTest {
    @Test
    fun `tilUtflatetKodeverk - flater ut valgte utdanningsprogram og sertifiseringer`() {
        val valgtLaerefagId = UUID.randomUUID()
        val ikkeValgtLaerefagId = UUID.randomUUID()
        val sertifiseringValg = setOf(SertifiseringValg(id = 1, navn = "Truckførerbevis"))

        val kodeverk = KodeverkResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                KodeverkResponse.Alternativ.Gruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Gruppe(
                            id = UUID.randomUUID(),
                            visningsnavn = "Helse- og oppvekstfag",
                            alternativer = listOf(
                                KodeverkResponse.Alternativ.Verdigruppe(
                                    id = UUID.randomUUID(),
                                    visningsnavn = "Lærefag",
                                    representerer = "larefag",
                                    seleksjonstype = KodeverkResponse.Seleksjonstype.FLERVALG,
                                    alternativer = listOf(
                                        KodeverkResponse.Alternativ.Verdi(
                                            id = valgtLaerefagId,
                                            visningsnavn = "Helsearbeiderfaget",
                                        ),
                                        KodeverkResponse.Alternativ.Verdi(
                                            id = ikkeValgtLaerefagId,
                                            visningsnavn = "Barne- og ungdomsarbeiderfaget",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                KodeverkResponse.Alternativ.VerdigruppeSok(
                    id = UUID.randomUUID(),
                    visningsnavn = "Sertifiseringer",
                    seleksjonstype = KodeverkResponse.Seleksjonstype.FLERVALG,
                    kilde = KodeverkResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING,
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtLaerefagId),
            sertifiseringValg = sertifiseringValg,
        )

        utflatetKodeverk shouldBe DeltakerlisteResponse.UtflatetKodeverk(
            tittel = "Helse- og oppvekstfag",
            valg = listOf("Helsearbeiderfaget", "Truckførerbevis"),
            valgteKodeverkIder = setOf(valgtLaerefagId),
            valgteSertifiseringer = sertifiseringValg,
        )
    }

    @Test
    fun `tilUtflatetKodeverk - bruker bransjenavn som tittel for bransjer`() {
        val valgtBransjeId = UUID.randomUUID()
        val valgtForerkortId = UUID.randomUUID()
        val sertifiseringValg = setOf(SertifiseringValg(id = 1, navn = "Truckførerbevis"))

        val kodeverk = KodeverkResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                KodeverkResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Bransje",
                    representerer = "bransje",
                    seleksjonstype = KodeverkResponse.Seleksjonstype.ENKELTVALG,
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Verdi(
                            id = valgtBransjeId,
                            visningsnavn = "Bygg og anlegg",
                        ),
                    ),
                ),
                KodeverkResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Førerkortklasse",
                    representerer = "forerkortklasse",
                    seleksjonstype = KodeverkResponse.Seleksjonstype.FLERVALG,
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Verdi(
                            id = valgtForerkortId,
                            visningsnavn = "B - Personbil",
                        ),
                        KodeverkResponse.Alternativ.Verdi(
                            id = UUID.randomUUID(),
                            visningsnavn = "C - Lastebil",
                        ),
                    ),
                ),
                KodeverkResponse.Alternativ.VerdigruppeSok(
                    id = UUID.randomUUID(),
                    visningsnavn = "Sertifiseringer",
                    seleksjonstype = KodeverkResponse.Seleksjonstype.FLERVALG,
                    kilde = KodeverkResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING,
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtBransjeId, valgtForerkortId),
            sertifiseringValg = sertifiseringValg,
        )

        utflatetKodeverk shouldBe DeltakerlisteResponse.UtflatetKodeverk(
            tittel = "Bygg og anlegg",
            valg = listOf("B - Personbil", "Truckførerbevis"),
            valgteKodeverkIder = setOf(valgtBransjeId, valgtForerkortId),
            valgteSertifiseringer = sertifiseringValg,
        )
    }

    @Test
    fun `tilUtflatetKodeverk - bruker kurstype som tittel`() {
        val valgtKurstypeId = UUID.randomUUID()

        val kodeverk = KodeverkResponse(
            tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            alternativer = listOf(
                KodeverkResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Kurstype",
                    representerer = "kurstype",
                    seleksjonstype = KodeverkResponse.Seleksjonstype.ENKELTVALG,
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Verdi(
                            id = valgtKurstypeId,
                            visningsnavn = "Gunnleggende ferdigheter",
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
            tittel = "Gunnleggende ferdigheter",
            valg = emptyList(),
            valgteKodeverkIder = setOf(valgtKurstypeId),
            valgteSertifiseringer = emptySet(),
        )
    }

    @Test
    fun `tilUtflatetKodeverk - tittel er tom streng når ingen bransje er valgt`() {
        val kodeverk = KodeverkResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                KodeverkResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Bransje",
                    representerer = "bransje",
                    seleksjonstype = KodeverkResponse.Seleksjonstype.ENKELTVALG,
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Verdi(
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
            tittel = null,
            valg = emptyList(),
            valgteKodeverkIder = emptySet(),
            valgteSertifiseringer = emptySet(),
        )
    }
}
