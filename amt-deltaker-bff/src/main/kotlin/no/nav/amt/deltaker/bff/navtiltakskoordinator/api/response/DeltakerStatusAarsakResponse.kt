package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class DeltakerStatusAarsakResponse(
    val type: DeltakerStatus.Aarsak.Type,
    val beskrivelse: String?,
) {
    constructor(model: DeltakerStatus.Aarsak) : this(
        type = model.type,
        beskrivelse = model.beskrivelse,
    )
}
