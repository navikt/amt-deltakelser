package no.nav.amt.deltaker.veileder.endring

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class VellykketEndring(
    val deltaker: Deltaker,
    val erFremtidigEndring: Boolean = false,
    val nesteStatus: DeltakerStatus? = null,
)
