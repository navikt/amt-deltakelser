package no.nav.amt.deltaker.api.tiltaksansvarlig

import java.util.UUID

data class DeltakerOppdateringResult(
    val deltakerId: UUID,
    val isSuccess: Boolean,
    val exception: Throwable?,
)
