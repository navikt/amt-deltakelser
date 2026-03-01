package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun DeltakerEndring.Endring.IkkeAktuell.hasChanges(deltaker: Deltaker) = deltaker.status.aarsak != this.aarsak.toDeltakerStatusAarsak()

fun DeltakerEndring.Endring.IkkeAktuell.ikkeAktuell(deltaker: Deltaker) = VellykketEndring(
    deltaker.copy(
        status = nyDeltakerStatus(
            type = DeltakerStatus.Type.IKKE_AKTUELL,
            aarsak = this.aarsak.toDeltakerStatusAarsak(),
        ),
        startdato = null,
        sluttdato = null,
    ),
)
