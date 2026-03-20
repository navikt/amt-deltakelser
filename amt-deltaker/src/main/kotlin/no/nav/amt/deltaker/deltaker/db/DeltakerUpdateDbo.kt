package no.nav.amt.deltaker.deltaker.db

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import java.time.LocalDate
import java.util.UUID

data class DeltakerUpdateDbo(
    val id: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
)
