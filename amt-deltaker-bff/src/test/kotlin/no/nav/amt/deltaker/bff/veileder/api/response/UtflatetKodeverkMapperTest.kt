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
                        KodeverkResponse.Alternativ.Verdigruppe(
                            id = UUID.randomUUID(),
                            visningsnavn = "Lærefag",
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

        utflatetKodeverk shouldBe UtflatetKodeverk(
            tittel = "Utdanningsprogram",
            valg = listOf("Helsearbeiderfaget", "Truckførerbevis"),
        )
    }

    @Test
    fun `tilUtflatetKodeverk - bruker visningsnavn som tittel for bransjer`() {
        val valgtBransjeId = UUID.randomUUID()
        val kodeverk = KodeverkResponse(
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            alternativer = listOf(
                KodeverkResponse.Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Bransje",
                    representerer = "bransjer",
                    seleksjonstype = KodeverkResponse.Seleksjonstype.ENKELTVALG,
                    alternativer = listOf(
                        KodeverkResponse.Alternativ.Verdi(
                            id = valgtBransjeId,
                            visningsnavn = "Bygg og anlegg",
                        ),
                    ),
                ),
            ),
        )

        val utflatetKodeverk = kodeverk.tilUtflatetKodeverk(
            kodeverkValg = setOf(valgtBransjeId),
            sertifiseringValg = emptySet(),
        )

        utflatetKodeverk shouldBe UtflatetKodeverk(
            tittel = "Bransje",
            valg = listOf("Bygg og anlegg"),
        )
    }
}
