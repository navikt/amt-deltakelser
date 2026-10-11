package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerStatusResponse(
    val id: UUID,
    val type: DeltakerStatus.Type,
    val aarsak: Aarsak?,
    val gyldigFra: LocalDateTime,
    val gyldigTil: LocalDateTime?,
    val opprettet: LocalDateTime,
) {
    data class Aarsak(
        val type: DeltakerStatus.Aarsak.Type,
        val beskrivelse: String?,
    ) {
        constructor(model: DeltakerStatus.Aarsak) : this(
            type = model.type,
            beskrivelse = model.beskrivelse,
        )
    }

    constructor(model: DeltakerStatus) : this(
        id = model.id,
        type = model.type,
        aarsak = model.aarsak?.let(::Aarsak),
        gyldigFra = model.gyldigFra,
        gyldigTil = model.gyldigTil,
        opprettet = model.opprettet,
    )
}
