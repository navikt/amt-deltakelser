package no.nav.amt.deltaker.repository.dbo

import java.time.LocalDateTime
import java.util.UUID

/**
 * En godkjent prisinfo slik den vises i deltakerhistorikken.
 *
 * @property prisinfo prisinformasjonen som ble godkjent
 * @property erForsteGodkjenning `true` for godkjenningen som fattet vedtaket,
 * `false` for påfølgende godkjenninger av prisendringer
 */
data class GodkjentPrisinfoDbo(
    val prisinfo: PrisinfoDbo,
    val sistEndret: LocalDateTime,
    val sistEndretAvNavAnsattId: UUID?,
    val sistEndretAvNavEnhetId: UUID?,
    val erForsteGodkjenning: Boolean,
)

