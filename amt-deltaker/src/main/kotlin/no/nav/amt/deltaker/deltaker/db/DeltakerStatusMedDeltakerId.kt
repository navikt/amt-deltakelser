package no.nav.amt.deltaker.deltaker.db

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

data class DeltakerStatusMedDeltakerId(
    val deltakerStatus: DeltakerStatus,
    val deltakerId: UUID,
)
