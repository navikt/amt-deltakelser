package no.nav.amt.lib.ktor.clients.kodeverk

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
}
