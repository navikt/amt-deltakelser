package no.nav.amt.deltaker.enkeltplass

import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.util.UUID

class OpplaringKategoriseringResponseExtensionsTest {
    @Test
    fun `tilvalgteKategoriseringerOgSertifiseringer - flater ut valgte utdanningsprogram`() {
        // Arrange
        val valgtUtdanningsprogramId = UUID.randomUUID()
        val valgtLaerefagId = UUID.randomUUID()

        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = listOf(
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = valgtUtdanningsprogramId,
                            visningsnavn = "Helse- og oppvekstfag",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = UUID.randomUUID(),
                                visningsnavn = "Lærefag",
                                representerer = OpplaringKategoriseringType.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = valgtLaerefagId,
                                        visningsnavn = "Helsearbeiderfaget",
                                    ),
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = UUID.randomUUID(),
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
        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = setOf(valgtUtdanningsprogramId, valgtLaerefagId),
            sertifiseringValg = emptySet(),
        )

        // Assert
        valgteKategoriseringerOgSertifiseringer shouldBe OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                    valg = mapOf(valgtUtdanningsprogramId to "Helse- og oppvekstfag"),
                ),
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.LAREFAG,
                    valg = mapOf(valgtLaerefagId to "Helsearbeiderfaget"),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )
    }

    @Test
    fun `tilvalgteKategoriseringerOgSertifiseringer - flater ut bransje og forerkort`() {
        // Arrange
        val valgtBransjeId = UUID.randomUUID()
        val valgtForerkortId = UUID.randomUUID()
        val sertifiseringValg = setOf(SertifiseringValg(id = 1, navn = "Truckførerbevis"))

        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Bransje",
                    pakrevd = true,
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
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
                    representerer = OpplaringKategoriseringType.FORERKORT,
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
                    pakrevd = false,
                    representerer = OpplaringKategoriseringType.SERTIFISERINGER,
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                    kilde = OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok.Kilde.JANZZ_SERTIFISERING,
                ),
            ),
        )

        // Act
        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = setOf(valgtBransjeId, valgtForerkortId),
            sertifiseringValg = sertifiseringValg,
        )

        // Assert
        valgteKategoriseringerOgSertifiseringer shouldBe OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valg = mapOf(valgtBransjeId to "Bygg og anlegg"),
                ),
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.FORERKORT,
                    valg = mapOf(valgtForerkortId to "B - Personbil"),
                ),
            ),
            valgteSertifiseringer = sertifiseringValg,
        )
    }

    @Test
    fun `tilvalgteKategoriseringerOgSertifiseringer - flater ut kurs`() {
        val valgtKurstypeId = UUID.randomUUID()

        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    pakrevd = true,
                    visningsnavn = "Kurstype",
                    representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                    seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                    alternativer = listOf(
                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                            id = valgtKurstypeId,
                            visningsnavn = "Grunnleggende ferdigheter",
                        ),
                        OpplaringKategoriseringResponse.Alternativ.Verdi(
                            id = UUID.randomUUID(),
                            visningsnavn = "Ikke valgt",
                        ),
                    ),
                ),
            ),
        )

        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = setOf(valgtKurstypeId),
            sertifiseringValg = emptySet(),
        )

        valgteKategoriseringerOgSertifiseringer shouldBe OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.KURSTYPE_ID,
                    valg = mapOf(valgtKurstypeId to "Grunnleggende ferdigheter"),
                ),
            ),
            valgteSertifiseringer = emptySet(),
        )
    }

    @Test
    fun `tilvalgteKategoriseringerOgSertifiseringer - tomt resultat nar ingenting er valgt`() {
        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    pakrevd = true,
                    visningsnavn = "Bransje",
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
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

        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = emptySet(),
            sertifiseringValg = emptySet(),
        )

        valgteKategoriseringerOgSertifiseringer shouldBe OpplaringKategoriseringValg(
            valgteKategoriseringer = emptySet(),
            valgteSertifiseringer = emptySet(),
        )
    }

    @Test
    fun `tilvalgteKategoriseringerOgSertifiseringer for UtdanningGruppe - ingen utdanninger valgt - returnerer tomt`() {
        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = listOf(
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = UUID.randomUUID(),
                            visningsnavn = "Program 1",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = UUID.randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringType.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = UUID.randomUUID(),
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

        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = emptySet(),
            sertifiseringValg = emptySet(),
        )

        valgteKategoriseringerOgSertifiseringer.valgteKategoriseringer shouldBe emptySet()
    }

    @Test
    fun `tilvalgteKategoriseringerOgSertifiseringer for UtdanningGruppe - tom utdanninger liste - returnerer tomt`() {
        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = emptyList(),
                ),
            ),
        )

        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = emptySet(),
            sertifiseringValg = emptySet(),
        )

        valgteKategoriseringerOgSertifiseringer.valgteKategoriseringer shouldBe emptySet()
    }

    @Test
    fun `kun utdanning valgt - returnerer utdanningsprogram og tomme larefag`() {
        val valgtUtdanningsprogramId = UUID.randomUUID()

        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = listOf(
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = valgtUtdanningsprogramId,
                            visningsnavn = "Valgt Program",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = UUID.randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringType.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = UUID.randomUUID(),
                                        visningsnavn = "Larefag 1",
                                        valgt = false,
                                    ),
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = UUID.randomUUID(),
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

        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = setOf(valgtUtdanningsprogramId),
            sertifiseringValg = emptySet(),
        )

        valgteKategoriseringerOgSertifiseringer.valgteKategoriseringer shouldBe setOf(
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                valg = mapOf(valgtUtdanningsprogramId to "Valgt Program"),
            ),
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.LAREFAG,
                valg = emptyMap(),
            ),
        )
    }

    @Test
    fun `tilvalgteKategoriseringerOgSertifiseringer for UtdanningGruppe - kun larefag valgt - returnerer utdanningsprogram og larefag`() {
        val valgtUtdanningsprogramId = UUID.randomUUID()
        val valgtLaerefagId1 = UUID.randomUUID()
        val valgtLaerefagId2 = UUID.randomUUID()

        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = listOf(
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = valgtUtdanningsprogramId,
                            visningsnavn = "Program med larefag",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = UUID.randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringType.LAREFAG,
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
                                        id = UUID.randomUUID(),
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

        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = setOf(valgtUtdanningsprogramId, valgtLaerefagId1, valgtLaerefagId2),
            sertifiseringValg = emptySet(),
        )

        valgteKategoriseringerOgSertifiseringer.valgteKategoriseringer shouldBe setOf(
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                valg = mapOf(valgtUtdanningsprogramId to "Program med larefag"),
            ),
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.LAREFAG,
                valg = mapOf(
                    valgtLaerefagId1 to "Larefag 1",
                    valgtLaerefagId2 to "Larefag 2",
                ),
            ),
        )
    }

    @Test
    fun `flere utdanninger men kun en har valgt larefag - returnerer riktig`() {
        val valgtUtdanningsprogramId1 = UUID.randomUUID()
        val valgtLaerefagId = UUID.randomUUID()

        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = listOf(
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = valgtUtdanningsprogramId1,
                            visningsnavn = "Program 1",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = UUID.randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringType.LAREFAG,
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
                            id = UUID.randomUUID(),
                            visningsnavn = "Program 2",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = UUID.randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringType.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = UUID.randomUUID(),
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

        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = setOf(valgtUtdanningsprogramId1, valgtLaerefagId),
            sertifiseringValg = emptySet(),
        )

        // Should find the first utdanning that has valgte larefag
        valgteKategoriseringerOgSertifiseringer.valgteKategoriseringer shouldBe setOf(
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                valg = mapOf(valgtUtdanningsprogramId1 to "Program 1"),
            ),
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.LAREFAG,
                valg = mapOf(valgtLaerefagId to "Larefag valgt"),
            ),
        )
    }

    @Test
    fun `larefag valgt i andre utdanning mens forste ikke har noe valgt - returnerer andre`() {
        val valgtUtdanningsprogramId2 = UUID.randomUUID()
        val valgtLaerefagId = UUID.randomUUID()

        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = listOf(
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = UUID.randomUUID(),
                            visningsnavn = "Program 1",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = UUID.randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringType.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = UUID.randomUUID(),
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
                                id = UUID.randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringType.LAREFAG,
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

        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = setOf(valgtUtdanningsprogramId2, valgtLaerefagId),
            sertifiseringValg = emptySet(),
        )

        valgteKategoriseringerOgSertifiseringer.valgteKategoriseringer shouldBe setOf(
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                valg = mapOf(valgtUtdanningsprogramId2 to "Program 2"),
            ),
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.LAREFAG,
                valg = mapOf(valgtLaerefagId to "Larefag fra program 2"),
            ),
        )
    }

    @Test
    fun `både utdanning og larefag valgt - returnerer begge`() {
        val valgtUtdanningsprogramId = UUID.randomUUID()
        val valgtLaerefagId = UUID.randomUUID()

        val opplaringKategoriseringResponse = OpplaringKategoriseringResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                    pakrevd = true,
                    utdanninger = listOf(
                        OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.UtdanningValg(
                            id = valgtUtdanningsprogramId,
                            visningsnavn = "Fullt valgt program",
                            larefag = OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                                id = UUID.randomUUID(),
                                visningsnavn = "Larefag",
                                representerer = OpplaringKategoriseringType.LAREFAG,
                                pakrevd = true,
                                seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.FLERVALG,
                                alternativer = listOf(
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = valgtLaerefagId,
                                        visningsnavn = "Valgt larefag",
                                        valgt = true,
                                    ),
                                    OpplaringKategoriseringResponse.Alternativ.Verdi(
                                        id = UUID.randomUUID(),
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

        val valgteKategoriseringerOgSertifiseringer = opplaringKategoriseringResponse.toOpplaringKategoriseringValg(
            kategoriseringValg = setOf(valgtUtdanningsprogramId, valgtLaerefagId),
            sertifiseringValg = emptySet(),
        )

        valgteKategoriseringerOgSertifiseringer.valgteKategoriseringer shouldBe setOf(
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
                valg = mapOf(valgtUtdanningsprogramId to "Fullt valgt program"),
            ),
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.LAREFAG,
                valg = mapOf(valgtLaerefagId to "Valgt larefag"),
            ),
        )
    }
}
