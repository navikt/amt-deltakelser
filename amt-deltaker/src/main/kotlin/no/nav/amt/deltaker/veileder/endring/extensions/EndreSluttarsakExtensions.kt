package no.nav.amt.deltaker.veileder.endring.extensions

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.veileder.endring.VellykketEndring
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
