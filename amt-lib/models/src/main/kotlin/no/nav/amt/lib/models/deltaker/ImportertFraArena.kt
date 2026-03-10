package no.nav.amt.lib.models.deltaker

import java.time.LocalDateTime
import java.util.UUID

data class ImportertFraArena(
    val deltakerId: UUID,
    val importertDato: LocalDateTime,
    val deltakerVedImport: DeltakerVedImport,
)
