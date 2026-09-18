package no.nav.amt.lib.models.deltaker

import java.time.LocalDateTime
import java.util.UUID

/**
 * Nye felter må ha default-verdier på grunn av JSON-lagring i databasen.
 *
 * `sistEndretAvNavAnsattId`/`sistEndretAvNavEnhetId` er nullable fordi godkjenneren ikke
 * alltid er kjent (f.eks. historiske data uten registrert saksbehandler).
 */
data class OkonomiGodkjentForHistorikk(
    val sistEndret: LocalDateTime,
    val sistEndretAvNavAnsattId: UUID? = null,
    val sistEndretAvNavEnhetId: UUID? = null,
    val erForsteGodkjenning: Boolean = false,
    val prisinformasjon: PrisinformasjonDto? = null,
)
