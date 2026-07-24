package no.nav.amt.lib.models.deltaker

import java.time.LocalDateTime
import java.util.UUID

data class OkonomiGodkjentForHistorikk(
    val sistEndret: LocalDateTime,
    val sistEndretAvNavAnsattId: UUID,
    val sistEndretAvNavEnhetId: UUID,
)
