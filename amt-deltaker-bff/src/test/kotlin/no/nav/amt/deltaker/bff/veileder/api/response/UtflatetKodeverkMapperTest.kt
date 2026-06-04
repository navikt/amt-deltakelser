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
        // Arrange
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

        // Act
        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtUtdanningsprogramId, valgtLaerefagId),
            sertifiseringValg = emptySet(),
        )

        // Assert
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
        // Arrange
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

        // Act
        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtBransjeId, valgtForerkortId),
            sertifiseringValg = sertifiseringValg,
        )

        // Assert
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

    @Test
    fun `tilUtflatetKodeverk for UtdanningGruppe - ingen utdanninger valgt - returnerer tomt`() {
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
                            id = randomUUID(),
                            visningsnavn = "Program 1",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = randomUUID(),
                                        visningsnavn = "Larefag 1",
                                        valgt = false,
                                    ),
                                ),
                            ),
                            valgt = false,
                        ),
                    ),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = emptySet(),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk.valgteKategoriseringer shouldBe emptySet()
    }

    @Test
    fun `tilUtflatetKodeverk for UtdanningGruppe - tom utdanninger liste - returnerer tomt`() {
        val kodeverk = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = emptyList(),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = emptySet(),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk.valgteKategoriseringer shouldBe emptySet()
    }

    @Test
    fun `tilUtflatetKodeverk for UtdanningGruppe - kun utdanning valgt - returnerer utdanningsprogram og tomme larefag`() {
        val valgtUtdanningsprogramId = randomUUID()

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
                            visningsnavn = "Valgt Program",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = randomUUID(),
                                        visningsnavn = "Larefag 1",
                                        valgt = false,
                                    ),
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = randomUUID(),
                                        visningsnavn = "Larefag 2",
                                        valgt = false,
                                    ),
                                ),
                            ),
                            valgt = true,
                        ),
                    ),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtUtdanningsprogramId),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk.valgteKategoriseringer shouldBe setOf(
            DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
                valg = mapOf(valgtUtdanningsprogramId to "Valgt Program"),
            ),
            DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                valg = emptyMap(),
            ),
        )
    }

    @Test
    fun `tilUtflatetKodeverk for UtdanningGruppe - kun larefag valgt - returnerer utdanningsprogram og larefag`() {
        val valgtUtdanningsprogramId = randomUUID()
        val valgtLaerefagId1 = randomUUID()
        val valgtLaerefagId2 = randomUUID()

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
                            visningsnavn = "Program med larefag",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = valgtLaerefagId1,
                                        visningsnavn = "Larefag 1",
                                        valgt = true,
                                    ),
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = valgtLaerefagId2,
                                        visningsnavn = "Larefag 2",
                                        valgt = true,
                                    ),
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = randomUUID(),
                                        visningsnavn = "Larefag 3",
                                        valgt = false,
                                    ),
                                ),
                            ),
                            valgt = false,
                        ),
                    ),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtUtdanningsprogramId, valgtLaerefagId1, valgtLaerefagId2),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk.valgteKategoriseringer shouldBe setOf(
            DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
                valg = mapOf(valgtUtdanningsprogramId to "Program med larefag"),
            ),
            DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                valg = mapOf(
                    valgtLaerefagId1 to "Larefag 1",
                    valgtLaerefagId2 to "Larefag 2",
                ),
            ),
        )
    }

    @Test
    fun `tilUtflatetKodeverk for UtdanningGruppe - flere utdanninger men kun en har valgt larefag - returnerer riktig`() {
        val valgtUtdanningsprogramId1 = randomUUID()
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
                            id = valgtUtdanningsprogramId1,
                            visningsnavn = "Program 1",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = valgtLaerefagId,
                                        visningsnavn = "Larefag valgt",
                                        valgt = true,
                                    ),
                                ),
                            ),
                            valgt = false,
                        ),
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = randomUUID(),
                            visningsnavn = "Program 2",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = randomUUID(),
                                        visningsnavn = "Larefag ikke valgt",
                                        valgt = false,
                                    ),
                                ),
                            ),
                            valgt = false,
                        ),
                    ),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtUtdanningsprogramId1, valgtLaerefagId),
            sertifiseringValg = emptySet(),
        )

        // Should find the first utdanning that has valgte larefag
        utflatetKodeverk.valgteKategoriseringer shouldBe setOf(
            DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
                valg = mapOf(valgtUtdanningsprogramId1 to "Program 1"),
            ),
            DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                valg = mapOf(valgtLaerefagId to "Larefag valgt"),
            ),
        )
    }

    @Test
    fun `tilUtflatetKodeverk for UtdanningGruppe - larefag valgt i andre utdanning mens forste ikke har noe valgt - returnerer andre`() {
        val valgtUtdanningsprogramId2 = randomUUID()
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
                            id = randomUUID(),
                            visningsnavn = "Program 1",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = randomUUID(),
                                        visningsnavn = "Larefag",
                                        valgt = false,
                                    ),
                                ),
                            ),
                            valgt = false,
                        ),
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = valgtUtdanningsprogramId2,
                            visningsnavn = "Program 2",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = valgtLaerefagId,
                                        visningsnavn = "Larefag fra program 2",
                                        valgt = true,
                                    ),
                                ),
                            ),
                            valgt = false,
                        ),
                    ),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtUtdanningsprogramId2, valgtLaerefagId),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk.valgteKategoriseringer shouldBe setOf(
            DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
                valg = mapOf(valgtUtdanningsprogramId2 to "Program 2"),
            ),
            DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                valg = mapOf(valgtLaerefagId to "Larefag fra program 2"),
            ),
        )
    }

    @Test
    fun `tilUtflatetKodeverk for UtdanningGruppe - både utdanning og larefag valgt - returnerer begge`() {
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
                            visningsnavn = "Fullt valgt program",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = valgtLaerefagId,
                                        visningsnavn = "Valgt larefag",
                                        valgt = true,
                                    ),
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = randomUUID(),
                                        visningsnavn = "Ikke valgt larefag",
                                        valgt = false,
                                    ),
                                ),
                            ),
                            valgt = true,
                        ),
                    ),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtUtdanningsprogramId, valgtLaerefagId),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk.valgteKategoriseringer shouldBe setOf(
            DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
                valg = mapOf(valgtUtdanningsprogramId to "Fullt valgt program"),
            ),
            DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
                representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
                valg = mapOf(valgtLaerefagId to "Valgt larefag"),
            ),
        )
    }
}
