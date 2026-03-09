package no.nav.amt.lib.models.tiltakskoordinator.requests

import java.util.UUID

data class DelMedArrangorRequest(
    override val endretAv: String,
    override val deltakerIder: List<UUID>,
) : EndringFraTiltakskoordinatorRequest
