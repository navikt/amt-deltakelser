package no.nav.amt.deltaker.api.tiltakskoordinator

import java.util.UUID

data class DeltakerOppdateringResult(
    val deltakerId: UUID,
    val isSuccess: Boolean,
    val exception: Throwable?,
)
