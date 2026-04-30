@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.bff.enkeltplass.kodeverk

import no.nav.amt.deltaker.bff.enkeltplass.kodeverk.Kodeverk.Alternativ
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.util.UUID

class KodeverkTest {
    @Test
    fun renderJsonTest() {
        val sut = Kodeverk(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            alternativer = listOf(
                Alternativ.Verdigruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Førerkortklasser",
                    seleksjonstype = Kodeverk.Seleksjonstype.FLERVALG,
                    alternativer = listOf(
                        Alternativ.Verdi(
                            id = UUID.randomUUID(),
                            visningsnavn = "B",
                        ),
                        Alternativ.Verdi(
                            id = UUID.randomUUID(),
                            visningsnavn = "C",
                        ),
                    ),
                ),
                Alternativ.Gruppe(
                    id = UUID.randomUUID(),
                    visningsnavn = "Utdanningsprogram",
                    alternativer = listOf(
                        Alternativ.Verdigruppe(
                            id = UUID.randomUUID(),
                            visningsnavn = "Frisør, blomster, interiør og ekspomeringsdesign",
                            seleksjonstype = Kodeverk.Seleksjonstype.ENKELTVALG,
                            alternativer = listOf(
                                Alternativ.Verdi(
                                    id = UUID.randomUUID(),
                                    visningsnavn = "Blomsterdekoratørfaget",
                                ),
                                Alternativ.Verdi(
                                    id = UUID.randomUUID(),
                                    visningsnavn = "Frisørfaget",
                                ),
                                Alternativ.Verdi(
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
