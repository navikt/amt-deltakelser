package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.lib.models.deltaker.DeltakerStatus

// Denne eksponerer mye mindre data enn DeltakerStatusResponse i amt-felles, så beholder den inntil videre.
data class DeltakerStatusResponse(
    val type: DeltakerStatus.Type,
    val aarsak: DeltakerStatusAarsakResponse?,
)
