package no.nav.amt.internapi.tiltakskoordinator.request

import java.util.UUID

data class DeltakereRequest(
    val gjennomforingId: UUID,
    val deltakere: List<UUID>,
    val endretAv: String,
)
