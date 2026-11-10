package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.internapi.deltaker.response.DeltakerStatusResponse.Aarsak
import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class DeltakerStatusResponse(
    val type: DeltakerStatus.Type,
    val aarsak: Aarsak?,
)
