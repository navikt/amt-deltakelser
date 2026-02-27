package no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.request

import java.util.UUID

data class DeltakereRequest(
    val deltakere: List<UUID>,
    val endretAv: String,
)
