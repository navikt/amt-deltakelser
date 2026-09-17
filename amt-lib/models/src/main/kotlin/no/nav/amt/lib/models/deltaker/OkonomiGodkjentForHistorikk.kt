package no.nav.amt.lib.models.deltaker

import java.time.LocalDateTime
import java.util.UUID

/**
 * Nye felter må ha default-verdier på grunn av JSON-lagring i databasen
 */
data class OkonomiGodkjentForHistorikk(
    val sistEndret: LocalDateTime,
    val sistEndretAvNavAnsattId: UUID?,
    val sistEndretAvNavEnhetId: UUID?,
    val erForsteGodkjenning: Boolean = false,
    val prisinformasjon: PrisinformasjonDto? = null,
)
