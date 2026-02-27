package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.time.LocalDateTime

fun DeltakerEndring.Endring.EndreAvslutning.hasChanges(deltaker: Deltaker): Boolean =
    (deltaker.status.type == DeltakerStatus.Type.FULLFORT && this.harFullfort == false) ||
        (deltaker.status.type == DeltakerStatus.Type.AVBRUTT && this.harFullfort == true) ||
        deltaker.sluttdato != this.sluttdato ||
        deltaker.status.aarsak != this.aarsak?.toDeltakerStatusAarsak()

fun DeltakerEndring.Endring.EndreAvslutning.endreAvslutning(deltaker: Deltaker): VellykketEndring =
    if (this.sluttdato != null && this.skalFortsattDelta() == true) {
        VellykketEndring(
            deltaker.copy(
                sluttdato = this.sluttdato!!,
                status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
            ),
            nesteStatus = this.getEndreAvslutningStatus(deltaker),
        )
    } else {
        VellykketEndring(
            deltaker.copy(
                status = this.getEndreAvslutningStatus(deltaker),
                sluttdato = this.sluttdato,
            ),
        )
    }

private fun DeltakerEndring.Endring.EndreAvslutning.skalFortsattDelta(): Boolean? = sluttdato?.let { !it.isBefore(LocalDate.now()) }

private fun DeltakerEndring.Endring.EndreAvslutning.getEndreAvslutningStatus(deltaker: Deltaker): DeltakerStatus {
    val nyDeltakerStatusType = deltaker.getAvsluttendeStatus(harFullfort == true)

    val gyldigFra = if (sluttdato != null && skalFortsattDelta() == true) {
        sluttdato!!.atStartOfDay().plusDays(1)
    } else {
        LocalDateTime.now()
    }

    return nyDeltakerStatus(
        type = nyDeltakerStatusType,
        aarsak = aarsak?.toDeltakerStatusAarsak(),
        gyldigFra = gyldigFra,
    )
}
