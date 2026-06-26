package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import java.time.LocalDate
import java.util.UUID

data class EnkeltplassDeltakerUpdateDbo(
    val id: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val dagerPerUke: Float?,
)
