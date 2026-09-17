package no.nav.amt.lib.models.deltaker

import java.time.LocalDateTime
import java.util.UUID

/**
 * @property erForsteGodkjenning `true` for den første godkjenningen av økonomi (den som fattet vedtaket),
 * `false` for påfølgende godkjenninger av prisendringer.
 * Default er `false` fordi modellen lagres som JSON i amt-tiltaksarrangor-bff.
 */
data class OkonomiGodkjentForHistorikk(
    val sistEndret: LocalDateTime,
    val sistEndretAvNavAnsattId: UUID,
    val sistEndretAvNavEnhetId: UUID,
    val erForsteGodkjenning: Boolean = false,
)
