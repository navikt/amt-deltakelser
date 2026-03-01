package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.time.LocalDateTime

fun DeltakerEndring.Endring.AvbrytDeltakelse.hasChanges(deltaker: Deltaker) = deltaker.status.type != DeltakerStatus.Type.AVBRUTT ||
    this.sluttdato != deltaker.sluttdato ||
    deltaker.status.aarsak != this.aarsak.toDeltakerStatusAarsak()

fun DeltakerEndring.Endring.AvbrytDeltakelse.avbrytDeltakelse(deltaker: Deltaker): VellykketEndring =
    if (deltaker.status.type == DeltakerStatus.Type.DELTAR || !this.skalFortsattDelta()) {
        VellykketEndring(
            deltaker.copy(
                sluttdato = this.sluttdato,
                status = this.getAvbruttStatus(),
            ),
        )
    } else {
        // Status er ikke Deltar, men deltakeren skal få deltar status
        VellykketEndring(
            deltaker.copy(
                sluttdato = this.sluttdato,
                status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
            ),
            nesteStatus = this.getAvbruttStatus(),
        )
    }

private fun DeltakerEndring.Endring.AvbrytDeltakelse.skalFortsattDelta(): Boolean = !sluttdato.isBefore(LocalDate.now())

private fun DeltakerEndring.Endring.AvbrytDeltakelse.getAvbruttStatus(): DeltakerStatus {
    val gyldigFra = if (skalFortsattDelta()) {
        sluttdato.atStartOfDay().plusDays(1)
    } else {
        LocalDateTime.now()
    }
    return nyDeltakerStatus(
        type = DeltakerStatus.Type.AVBRUTT,
        aarsak = aarsak.toDeltakerStatusAarsak(),
        gyldigFra = gyldigFra,
    )
}
