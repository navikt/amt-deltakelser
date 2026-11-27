package no.nav.amt.deltaker.veileder.endring.extensions

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import java.time.LocalDate

fun Deltaker.getAvsluttendeStatus(harFullfort: Boolean): DeltakerStatus.Type = when {
    gjennomforing.erFellesOppstart || deltarPaOpplaeringstiltak ->
        if (harFullfort) DeltakerStatus.Type.FULLFORT else DeltakerStatus.Type.AVBRUTT

    else -> DeltakerStatus.Type.HAR_SLUTTET
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
    fun Deltaker.oppdaterDeltakerStatusEndreOppstart(
        nyStartdato: LocalDate?,
        nySluttdato: LocalDate?,
    ): DeltakerStatus {
        fun nyStatusHvisEndret(eksisterendeStatus: DeltakerStatus.Type) =
            if (status.type == eksisterendeStatus) status else nyDeltakerStatus(eksisterendeStatus)

        // SkalBliIkkeAktuell er kun for Arena-deltakere
        fun Deltaker.skalBliIkkeAktuell(
            startdato: LocalDate?,
            sluttdato: LocalDate?,
            now: LocalDate,
        ): Boolean = status.type == DeltakerStatus.Type.VENTER_PA_OPPSTART &&
            startdato == null &&
            sluttdato.erPassert(now)

        if (status.type == DeltakerStatus.Type.SOKT_INN) return status

        val now = LocalDate.now()

        return when {
            skalBliIkkeAktuell(
                startdato = nyStartdato,
                sluttdato = nySluttdato,
                now = now,
            ) -> nyStatusHvisEndret(DeltakerStatus.Type.IKKE_AKTUELL)

            !nySluttdato.erPassert(now) && (nyStartdato == null || nyStartdato.isAfter(now)) ->
                nyStatusHvisEndret(DeltakerStatus.Type.VENTER_PA_OPPSTART)

            nyStartdato != null && !nyStartdato.isAfter(now) && !nySluttdato.erPassert(now) ->
                nyStatusHvisEndret(DeltakerStatus.Type.DELTAR)

            nySluttdato.erPassert(now) ->
                nyStatusHvisEndret(getAvsluttendeStatus(harFullfort = status.type != DeltakerStatus.Type.AVBRUTT))

            else -> status
        }
    }

    val faktiskSluttdato = sluttdato ?: this.sluttdato
    val oppdatertStatus = this.oppdaterDeltakerStatusEndreOppstart(
        nyStartdato = startdato,
        nySluttdato = faktiskSluttdato,
    )

    val (startdato, sluttdato) = if (oppdatertStatus.type == DeltakerStatus.Type.IKKE_AKTUELL) {
        null to null
    } else {
        startdato to faktiskSluttdato
    }

    val oppdatertDeltakelsmengde = deltakelsesmengder.avgrensPeriodeTilStartdato(startdato)
    // Før fremtidig oppstart finnes ingen gjeldende mengde i dag. Lagre da den første
    // mengden fra ny startdato slik at den kan vises også mens deltakeren venter på oppstart.
    val forsteGjeldendeMengde = oppdatertDeltakelsmengde.gjeldende ?: oppdatertDeltakelsmengde.firstOrNull()

    // Prosent og dager hører til samme periode. Null i en eksisterende periode betyr at feltet
    // er tømt; behold gamle verdier bare når historikken ikke gir noen mengde.
    val (nyDeltakelsesprosent, nyeDagerPerUke) = forsteGjeldendeMengde
        ?.let { it.deltakelsesprosent to it.dagerPerUke }
        ?: (deltakelsesprosent to dagerPerUke)

    return this.copy(
        startdato = startdato,
        sluttdato = sluttdato,
        status = oppdatertStatus,
        deltakelsesprosent = nyDeltakelsesprosent,
        dagerPerUke = nyeDagerPerUke,
    )
}

private fun LocalDate?.erPassert(now: LocalDate): Boolean = this != null && this < now
