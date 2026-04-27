package no.nav.amt.lib.models.deltaker.extensions

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate
import java.time.LocalDateTime

private fun List<DeltakerHistorikk>.getInnsoktDatoFraImportertDeltaker(): LocalDate? =
    filterIsInstance<DeltakerHistorikk.ImportertFraArena>()
        .firstOrNull()
        ?.importertFraArena
        ?.deltakerVedImport
        ?.innsoktDato

private fun List<DeltakerHistorikk>.getInnsoktDatoFraInnsok(): LocalDateTime? =
    filterIsInstance<DeltakerHistorikk.InnsokPaaFellesOppstart>()
        .firstOrNull()
        ?.data
        ?.innsokt

fun List<DeltakerHistorikk>.getInnsoktDato(): LocalDateTime? {
    getInnsoktDatoFraImportertDeltaker()?.let { return it.atStartOfDay() }
    getInnsoktDatoFraInnsok()?.let { return it }

    return filterIsInstance<DeltakerHistorikk.Vedtak>()
        .map { it.vedtak }
        .minByOrNull { it.opprettet }
        ?.opprettet
}
