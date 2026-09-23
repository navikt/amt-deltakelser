package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde.Companion.FALLBACK_DELTAKELSESPROSENT
import java.time.LocalDate
import java.time.LocalDateTime

data class Deltakelsesmengde(
    val deltakelsesprosent: Float,
    val dagerPerUke: Float?,
    val gyldigFra: LocalDate,
    val opprettet: LocalDateTime,
) {
    companion object {
        const val EMPTY_DELTAKELSESPROSENT = -1F
        const val FALLBACK_DELTAKELSESPROSENT = 100F
    }
}

fun DeltakerEndring.toDeltakelsesmengde(): Deltakelsesmengde? = when (val endring = this.endring) {
    is DeltakerEndring.Endring.EndreDeltakelsesmengde -> endring.toDeltakelsesmengde(this.endret)
    else -> null
}

fun DeltakerEndring.Endring.EndreDeltakelsesmengde.toDeltakelsesmengde(opprettet: LocalDateTime) = Deltakelsesmengde(
    deltakelsesprosent = this.deltakelsesprosent ?: FALLBACK_DELTAKELSESPROSENT,
    dagerPerUke = this.dagerPerUke,
    gyldigFra = this.gyldigFra ?: opprettet.toLocalDate(),
    opprettet = opprettet,
)

fun Vedtak.toDeltakelsesmengde() = this.deltakerVedVedtak.deltakelsesprosent?.let {
    Deltakelsesmengde(
        deltakelsesprosent = this.deltakerVedVedtak.deltakelsesprosent,
        dagerPerUke = this.deltakerVedVedtak.dagerPerUke,
        gyldigFra = this.fattet?.toLocalDate() ?: this.opprettet.toLocalDate(),
        opprettet = this.fattet ?: this.opprettet,
    )
}

fun ImportertFraArena.toDeltakelsesmengde() = this.deltakerVedImport.deltakelsesprosent?.let {
    Deltakelsesmengde(
        deltakelsesprosent = this.deltakerVedImport.deltakelsesprosent,
        dagerPerUke = this.deltakerVedImport.dagerPerUke,
        gyldigFra = this.deltakerVedImport.innsoktDato,
        opprettet = this.deltakerVedImport.innsoktDato.atStartOfDay(),
    )
}
