package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring

fun DeltakerEndring.Endring.EndreBakgrunnsinformasjon.hasChanges(deltaker: Deltaker): Boolean =
    deltaker.bakgrunnsinformasjon != this.bakgrunnsinformasjon

fun DeltakerEndring.Endring.EndreBakgrunnsinformasjon.endreBakgrunnsinformasjon(deltaker: Deltaker): VellykketEndring =
    VellykketEndring(deltaker.copy(bakgrunnsinformasjon = this.bakgrunnsinformasjon))
