package no.nav.amt.lib.models.tiltakskoordinator.requests

import java.util.UUID

sealed interface EndringFraTiltakskoordinatorRequest {
    val endretAv: String
    val deltakerIder: List<UUID>
}
