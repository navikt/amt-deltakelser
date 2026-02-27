package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class VellykketEndring(
    val deltaker: Deltaker,
    val erFremtidigEndring: Boolean = false,
    val nesteStatus: DeltakerStatus? = null,
)
