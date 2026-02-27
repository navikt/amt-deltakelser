package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring

fun DeltakerEndring.Endring.EndreInnhold.hasChanges(deltaker: Deltaker): Boolean = deltaker.deltakelsesinnhold?.innhold != this.innhold

fun DeltakerEndring.Endring.EndreInnhold.endreInnhold(deltaker: Deltaker) = VellykketEndring(
    deltaker.copy(deltakelsesinnhold = Deltakelsesinnhold(this.ledetekst, this.innhold)),
)
