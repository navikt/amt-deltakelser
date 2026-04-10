package no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions

import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerStatusAarsakResponse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerStatusResponse
import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun DeltakerStatus.toResponse() = DeltakerStatusResponse(
    type = type,
    aarsak = aarsak?.let {
        DeltakerStatusAarsakResponse(
            it.type,
            it.beskrivelse,
        )
    },
)
