package no.nav.amt.lib.models.deltaker

import java.time.LocalDate
import java.util.UUID

data class DeltakerVedVedtak(
    val id: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val status: DeltakerStatus,
)
