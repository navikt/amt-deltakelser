package no.nav.amt.lib.models.deltaker

import java.time.LocalDateTime
import java.util.UUID

data class ImportertFraArena(
    val deltakerId: UUID,
    val importertDato: LocalDateTime,
    val deltakerVedImport: DeltakerVedImport,
) {
    val innsoktDatoAsLocalDateTime: LocalDateTime
        // hvis importertDato og innsoktDato er på samme dag, benyttes
        // importertDato for å få med tidspunktet
        // ellers benyttes innsoktDato satt til midnatt
        get() = if (deltakerVedImport.innsoktDato == importertDato.toLocalDate()) {
            importertDato
        } else {
            deltakerVedImport.innsoktDato.atStartOfDay()
        }
}
