package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate

data class ImportertFraArenaResponse(
    val innsoktDato: LocalDate,
) {
    companion object {
        fun fromDeltaker(deltaker: Deltaker): ImportertFraArenaResponse? = deltaker.historikk
            .filterIsInstance<DeltakerHistorikk.ImportertFraArena>()
            .firstOrNull()
            ?.let {
                ImportertFraArenaResponse(it.importertFraArena.deltakerVedImport.innsoktDato)
            }

        fun fromDeltaker(deltaker: DeltakerModel): ImportertFraArenaResponse? = deltaker.historikk
            .filterIsInstance<DeltakerHistorikk.ImportertFraArena>()
            .firstOrNull()
            ?.let {
                ImportertFraArenaResponse(it.importertFraArena.deltakerVedImport.innsoktDato)
            }
    }
}
