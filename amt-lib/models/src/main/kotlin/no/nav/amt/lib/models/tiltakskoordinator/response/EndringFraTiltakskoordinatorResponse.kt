package no.nav.amt.lib.models.tiltakskoordinator.response

import java.time.LocalDateTime
import java.util.UUID

data class EndringFraTiltakskoordinatorResponse(
    val id: UUID,
    val erDeltManueltMedArrangor: Boolean,
    val sistEndret: LocalDateTime,
)
