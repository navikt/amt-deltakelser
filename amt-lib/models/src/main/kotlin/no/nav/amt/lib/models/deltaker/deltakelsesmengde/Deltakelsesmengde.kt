package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Vedtak
import java.time.LocalDate
import java.time.LocalDateTime

data class Deltakelsesmengde(
    val deltakelsesprosent: Float?,
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
    deltakelsesprosent = this.deltakelsesprosent,
    dagerPerUke = this.dagerPerUke,
    gyldigFra = this.gyldigFra ?: opprettet.toLocalDate(),
    opprettet = opprettet,
)

fun Vedtak.toDeltakelsesmengde() = this.deltakerVedVedtak
    .takeUnless { it.deltakelsesprosent == null && it.dagerPerUke == null }
    ?.let {
        Deltakelsesmengde(
            deltakelsesprosent = it.deltakelsesprosent,
            dagerPerUke = it.dagerPerUke,
            gyldigFra = this.fattet?.toLocalDate() ?: this.opprettet.toLocalDate(),
            opprettet = this.fattet ?: this.opprettet,
        )
    }

fun ImportertFraArena.toDeltakelsesmengde() = this.deltakerVedImport
    .takeUnless { it.deltakelsesprosent == null && it.dagerPerUke == null }
    ?.let {
        Deltakelsesmengde(
            deltakelsesprosent = it.deltakelsesprosent,
            dagerPerUke = it.dagerPerUke,
            gyldigFra = it.innsoktDato,
            opprettet = it.innsoktDato.atStartOfDay(),
        )
    }
