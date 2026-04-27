package no.nav.amt.lib.models.deltaker.extensions

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate
import java.time.LocalDateTime

fun List<DeltakerHistorikk>.getInnsoktDatoFraImportertDeltaker(): LocalDate? = filterIsInstance<DeltakerHistorikk.ImportertFraArena>()
    .minOfOrNull { it.importertFraArena.deltakerVedImport.innsoktDato }

private fun List<DeltakerHistorikk>.getInnsoktDatoFraInnsok(): LocalDateTime? =
    filterIsInstance<DeltakerHistorikk.InnsokPaaFellesOppstart>()
        .minOfOrNull { it.data.innsokt }

fun List<DeltakerHistorikk>.getInnsoktDato(): LocalDateTime? {
    getInnsoktDatoFraImportertDeltaker()?.let { return it.atStartOfDay() }
    getInnsoktDatoFraInnsok()?.let { return it }

    return filterIsInstance<DeltakerHistorikk.Vedtak>()
        .minOfOrNull { it.vedtak.opprettet }
}
