package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.time.LocalDateTime

fun DeltakerEndring.Endring.AvsluttDeltakelse.hasChanges(deltaker: Deltaker): Boolean =
    deltaker.status.type != DeltakerStatus.Type.HAR_SLUTTET ||
        this.sluttdato != deltaker.sluttdato ||
        this.aarsak?.toDeltakerStatusAarsak() != deltaker.status.aarsak

fun DeltakerEndring.Endring.AvsluttDeltakelse.avsluttDeltakelse(deltaker: Deltaker): VellykketEndring =
    // Skal deltaker avsluttes nå eller i fremtiden?
    if (deltaker.status.type == DeltakerStatus.Type.DELTAR || !this.skalFortsattDelta()) {
        VellykketEndring(
            deltaker.copy(
                sluttdato = this.sluttdato,
                status = this.getAvsluttendeStatus(deltaker),
            ),
        )
    } else {
        // Deltaker er avsluttet allerede, men Nav-veileder godkjenner et forslag om å avslutte deltaker frem i tid
        // Da settes status til DELTAR igjen med en fremtidig(neste) avsluttende status
        VellykketEndring(
            deltaker.copy(
                sluttdato = this.sluttdato,
                status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
            ),
            nesteStatus = this.getAvsluttendeStatus(deltaker),
        )
    }

private fun DeltakerEndring.Endring.AvsluttDeltakelse.skalFortsattDelta(): Boolean = !sluttdato.isBefore(LocalDate.now())

private fun DeltakerEndring.Endring.AvsluttDeltakelse.getAvsluttendeStatus(deltaker: Deltaker): DeltakerStatus {
    val gyldigFra = if (skalFortsattDelta()) {
        sluttdato.atStartOfDay().plusDays(1)
    } else {
        LocalDateTime.now()
    }
    return nyDeltakerStatus(
        type = if (deltaker.deltakerliste.erFellesOppstart || deltaker.deltarPaOpplaeringstiltak) {
            DeltakerStatus.Type.FULLFORT
        } else {
            DeltakerStatus.Type.HAR_SLUTTET
        },
        aarsak = aarsak?.toDeltakerStatusAarsak(),
        gyldigFra = gyldigFra,
    )
}
