package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import java.time.LocalDate

fun Deltaker.getAvsluttendeStatus(harFullfort: Boolean): DeltakerStatus.Type = when {
    deltakerliste.erFellesOppstart || deltarPaOpplaeringstiltak -> {
        if (harFullfort) DeltakerStatus.Type.FULLFORT else DeltakerStatus.Type.AVBRUTT
    }

    else -> {
        DeltakerStatus.Type.HAR_SLUTTET
    }
}

fun Deltaker.getStatusEndretSluttdato(sluttdato: LocalDate): DeltakerStatus =
    if (status.type in listOf(DeltakerStatus.Type.HAR_SLUTTET, DeltakerStatus.Type.AVBRUTT, DeltakerStatus.Type.FULLFORT) &&
        !sluttdato.isBefore(LocalDate.now())
    ) {
        nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
    } else {
        status
    }

fun Deltaker.endreDeltakersOppstart(
    startdato: LocalDate?,
    sluttdato: LocalDate?,
    deltakelsesmengder: Deltakelsesmengder,
): Deltaker {
    fun Deltaker.oppdaterDeltakerStatusEndreOppstart(nyStartdato: LocalDate?, nySluttdato: LocalDate?): DeltakerStatus {
        // SkalBliIkkeAktuell er kun for Arena deltakere
        fun Deltaker.skalBliIkkeAktuell(
            startdato: LocalDate?,
            sluttdato: LocalDate?,
            now: LocalDate,
        ): Boolean = status.type == DeltakerStatus.Type.VENTER_PA_OPPSTART &&
            startdato == null &&
            sluttdato.erPassert(now)

        val now = LocalDate.now()

        return when {
            skalBliIkkeAktuell(nyStartdato, nySluttdato, now) -> nyDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL)
            !nyStartdato.erPassert(now) && !nySluttdato.erPassert(now) -> nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
            nyStartdato.erPassert(now) && !nySluttdato.erPassert(now) -> nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
            nySluttdato.erPassert(now) -> nyDeltakerStatus(getAvsluttendeStatus(harFullfort = status.type != DeltakerStatus.Type.AVBRUTT))
            else -> status
        }
    }

    val faktiskSluttdato = sluttdato ?: this.sluttdato
    val oppdatertStatus = this.oppdaterDeltakerStatusEndreOppstart(
        nyStartdato = startdato,
        nySluttdato = faktiskSluttdato,
    )
    val oppdatertDeltakelsmengde = deltakelsesmengder.avgrensPeriodeTilStartdato(startdato)

    return this.copy(
        startdato = if (oppdatertStatus.type == DeltakerStatus.Type.IKKE_AKTUELL) null else startdato,
        sluttdato = if (oppdatertStatus.type == DeltakerStatus.Type.IKKE_AKTUELL) null else faktiskSluttdato,
        status = oppdatertStatus,
        deltakelsesprosent = oppdatertDeltakelsmengde.gjeldende?.deltakelsesprosent,
        dagerPerUke = oppdatertDeltakelsmengde.gjeldende?.dagerPerUke,
    )
}

private fun LocalDate?.erPassert(now: LocalDate): Boolean = this != null && this < now
