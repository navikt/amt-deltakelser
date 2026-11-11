package no.nav.amt.deltaker.repository.dbo

import java.time.LocalDateTime
import java.util.UUID

/** En godkjent prisinfo med godkjenner og tidspunkt. */
data class GodkjentPrisinfoDbo(
    val prisinfo: PrisinfoDbo,
    val sistEndret: LocalDateTime,
    val godkjentAvNavAnsattId: UUID,
    val godkjentAvNavEnhetId: UUID,
)
