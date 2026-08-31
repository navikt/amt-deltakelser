package no.nav.tiltaksarrangor.api.response

import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

// kopiert fra amt-deltaker-bff
data class OpplaringKategoriseringValgResponse(
    val valgteKategoriseringer: Set<Kategorisering>,
    val valgteSertifiseringer: Set<SertifiseringValgResponse>,
) {
    constructor(model: OpplaringKategoriseringValg) : this(
        valgteKategoriseringer = model.valgteKategoriseringer.map(::Kategorisering).toSet(),
        valgteSertifiseringer = model.valgteSertifiseringer.map(::SertifiseringValgResponse).toSet(),
    )

    data class Kategorisering(
        val type: OpplaringKategoriseringType,
        val valgteElementer: List<Valg>,
    ) {
        constructor(model: OpplaringKategoriseringValg.ValgteFelt) : this(
            type = model.representerer,
            valgteElementer = model.valg.entries.map(::Valg),
        )
    }

    data class Valg(
        val id: UUID,
        val visningsnavn: String,
    ) {
        constructor(model: Map.Entry<UUID, String>) : this(
            id = model.key,
            visningsnavn = model.value,
        )
    }

    data class SertifiseringValgResponse(
        val id: Long,
        val navn: String,
    ) {
        constructor(model: SertifiseringValg) : this(
            id = model.id,
            navn = model.navn,
        )
    }

    companion object {
        fun fromModel(opplaringKategoriseringValg: OpplaringKategoriseringValg): OpplaringKategoriseringValgResponse =
            OpplaringKategoriseringValgResponse(opplaringKategoriseringValg)
    }
}
