package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde.Companion.EMPTY_DELTAKELSESPROSENT
import java.time.LocalDateTime

fun DeltakerHistorikk.toDeltakelsesmengdeEkstern() = when (this) {
    is DeltakerHistorikk.ImportertFraArena -> this.importertFraArena.toDeltakelsesmengdeEkstern()
    is DeltakerHistorikk.Endring -> this.endring.toDeltakelsesmengdeEkstern()
    is DeltakerHistorikk.Vedtak -> this.vedtak.toDeltakelsesmengdeEkstern()
    else -> null
}

fun DeltakerEndring.toDeltakelsesmengdeEkstern(): Deltakelsesmengde? = when (val endring = this.endring) {
    is DeltakerEndring.Endring.EndreDeltakelsesmengde -> endring.toDeltakelsesmengdeEkstern(this.endret)
    else -> null
}

fun DeltakerEndring.Endring.EndreDeltakelsesmengde.toDeltakelsesmengdeEkstern(opprettet: LocalDateTime): Deltakelsesmengde? = this
    .takeUnless { it.deltakelsesprosent == null && it.dagerPerUke == null }
    ?.let {
        Deltakelsesmengde(
            deltakelsesprosent = it.deltakelsesprosent ?: EMPTY_DELTAKELSESPROSENT,
            dagerPerUke = it.dagerPerUke,
            gyldigFra = it.gyldigFra ?: opprettet.toLocalDate(),
            opprettet = opprettet,
        )
    }

fun Vedtak.toDeltakelsesmengdeEkstern(): Deltakelsesmengde? = this.deltakerVedVedtak
    .takeUnless { it.deltakelsesprosent == null && it.dagerPerUke == null }
    ?.let {
        Deltakelsesmengde(
            deltakelsesprosent = it.deltakelsesprosent ?: EMPTY_DELTAKELSESPROSENT,
            dagerPerUke = it.dagerPerUke,
            gyldigFra = this.fattet?.toLocalDate() ?: this.opprettet.toLocalDate(),
            opprettet = this.fattet ?: this.opprettet,
        )
    }

fun ImportertFraArena.toDeltakelsesmengdeEkstern(): Deltakelsesmengde? = this.deltakerVedImport
    .takeUnless { it.deltakelsesprosent == null && it.dagerPerUke == null }
    ?.let {
        Deltakelsesmengde(
            deltakelsesprosent = it.deltakelsesprosent ?: EMPTY_DELTAKELSESPROSENT,
            dagerPerUke = it.dagerPerUke,
            gyldigFra = it.innsoktDato,
            opprettet = it.innsoktDato.atStartOfDay(),
        )
    }
