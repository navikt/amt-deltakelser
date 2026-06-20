package no.nav.amt.internapi.enkeltplass

import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

/**
 * Benyttes både i APIer mot frontend, og i intern-APIer.
 *
 */
data class OpplaringKategoriseringValg(
    val valgteKategoriseringer: Set<ValgteFelt>,
    val valgteSertifiseringer: Set<SertifiseringValg>,
) {
    data class ValgteFelt(
        val representerer: OpplaringKategoriseringResponse.Representerer,
        val valg: Map<UUID, String>,
    )
}
