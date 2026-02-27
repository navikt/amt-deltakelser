package no.nav.amt.lib.models.deltaker.internalapis.deltaker.response

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

data class DeltakerMedStatusResponse(
    val id: UUID,
    val status: DeltakerStatus,
)
