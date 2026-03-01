package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring

fun DeltakerEndring.Endring.EndreSluttarsak.hasChanges(deltaker: Deltaker): Boolean =
    deltaker.status.aarsak != this.aarsak.toDeltakerStatusAarsak()

fun DeltakerEndring.Endring.EndreSluttarsak.endreSluttarsak(deltaker: Deltaker) = VellykketEndring(
    deltaker.copy(
        status = nyDeltakerStatus(
            type = deltaker.status.type,
            aarsak = this.aarsak.toDeltakerStatusAarsak(),
        ),
    ),
)
