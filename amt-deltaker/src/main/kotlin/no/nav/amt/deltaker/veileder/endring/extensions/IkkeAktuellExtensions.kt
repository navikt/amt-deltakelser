package no.nav.amt.deltaker.veileder.endring.extensions

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.veileder.endring.VellykketEndring
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun DeltakerEndring.Endring.IkkeAktuell.hasChanges(deltaker: Deltaker) = deltaker.status.type != DeltakerStatus.Type.IKKE_AKTUELL ||
    deltaker.status.aarsak != this.aarsak.toDeltakerStatusAarsak()

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
