package no.nav.amt.lib.models.tiltakskoordinator.requests

import java.util.UUID

data class DelMedArrangorRequest(
    val endretAv: String,
    val deltakerIder: List<UUID>,
    val gjennomforingId: UUID,
)
