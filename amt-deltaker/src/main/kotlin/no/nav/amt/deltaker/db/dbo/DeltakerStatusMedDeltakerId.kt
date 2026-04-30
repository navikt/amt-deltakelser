package no.nav.amt.deltaker.db.dbo

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

data class DeltakerStatusMedDeltakerId(
    val deltakerStatus: DeltakerStatus,
    val deltakerId: UUID,
)
