package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.model.Deltaker

data class DeltakerOppdateringResult(
    val deltaker: Deltaker,
    val isSuccess: Boolean,
    val exception: Throwable?,
)
