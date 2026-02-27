package no.nav.amt.deltaker.deltaker.extensions

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate
import java.time.LocalDateTime

fun List<DeltakerHistorikk>.getInnsoktDatoFraImportertDeltaker(): LocalDate? = filterIsInstance<DeltakerHistorikk.ImportertFraArena>()
    .firstOrNull()
    ?.importertFraArena
    ?.deltakerVedImport
    ?.innsoktDato

fun List<DeltakerHistorikk>.getInnsoktDatoFraInnsok(): LocalDateTime? = filterIsInstance<DeltakerHistorikk.InnsokPaaFellesOppstart>()
    .firstOrNull()
    ?.data
    ?.innsokt

fun List<DeltakerHistorikk>.getInnsoktDato(): LocalDateTime? {
    getInnsoktDatoFraImportertDeltaker()?.let { return it.atStartOfDay() }
    getInnsoktDatoFraInnsok()?.let { return it }

    val vedtak = filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
    return vedtak.minByOrNull { it.opprettet }?.opprettet
}
