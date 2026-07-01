package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

data class OpplaringKategoriseringValgResponse(
    val valgteKategoriseringer: Set<Kategorisering>,
    val valgteSertifiseringer: Set<SertifiseringValg>,
) {
    data class Kategorisering(
        val type: OpplaringKategoriseringType,
        val valgteElementer: List<Valg>,
    )

    data class Valg(
        val id: UUID,
        val visningsnavn: String,
    )

    companion object {
        fun fromOpplaringKategoriseringValg(valg: OpplaringKategoriseringValg?): OpplaringKategoriseringValgResponse? {
            if (valg == null) return null

            return OpplaringKategoriseringValgResponse(
                valgteKategoriseringer = valg.valgteKategoriseringer
                    .map { felt ->
                        Kategorisering(
                            type = felt.representerer,
                            valgteElementer = felt.valg.map { (id, visningsnavn) ->
                                Valg(id = id, visningsnavn = visningsnavn)
                            },
                        )
                    }.toSet(),
                valgteSertifiseringer = valg.valgteSertifiseringer,
            )
        }
    }
}
