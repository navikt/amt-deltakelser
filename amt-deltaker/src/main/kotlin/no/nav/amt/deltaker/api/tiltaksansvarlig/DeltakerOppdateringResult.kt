package no.nav.amt.deltaker.api.tiltaksansvarlig

import no.nav.amt.deltaker.model.Deltaker

data class DeltakerOppdateringResult(
    val deltaker: Deltaker,
    val isSuccess: Boolean,
    val exception: Throwable?,
)
