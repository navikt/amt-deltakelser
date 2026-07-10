package no.nav.amt.internapi.tiltakskoordinator.response

import no.nav.amt.internapi.tiltakskoordinator.HandlingFilterValg
import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class DeltakerlisteFilterCountsResponse(
    val statusCounts: Map<DeltakerStatus.Type, Int>,
    val handlingCounts: Map<HandlingFilterValg, Int>,
)
