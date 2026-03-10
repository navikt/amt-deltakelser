package no.nav.amt.api.tiltakskoordinator.request

import java.util.UUID

data class DeltakereRequest(
    val deltakere: List<UUID>,
    val endretAv: String,
)
