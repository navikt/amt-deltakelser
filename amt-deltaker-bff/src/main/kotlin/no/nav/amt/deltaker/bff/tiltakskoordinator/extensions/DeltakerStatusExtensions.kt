package no.nav.amt.deltaker.bff.tiltakskoordinator.extensions

import no.nav.amt.deltaker.bff.tiltakskoordinator.api.response.DeltakerStatusAarsakResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.response.DeltakerStatusResponse
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
