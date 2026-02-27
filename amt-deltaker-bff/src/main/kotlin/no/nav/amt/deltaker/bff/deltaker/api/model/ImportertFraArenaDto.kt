package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate

data class ImportertFraArenaDto(
    val innsoktDato: LocalDate,
) {
    companion object {
        fun fromDeltaker(deltaker: Deltaker): ImportertFraArenaDto? = deltaker.historikk
            .filterIsInstance<DeltakerHistorikk.ImportertFraArena>()
            .firstOrNull()
            ?.let {
                ImportertFraArenaDto(it.importertFraArena.deltakerVedImport.innsoktDato)
            }
    }
}
