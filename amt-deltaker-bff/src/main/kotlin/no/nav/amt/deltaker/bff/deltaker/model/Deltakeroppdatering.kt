package no.nav.amt.deltaker.bff.deltaker.model

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Deltakeroppdatering(
    val id: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val status: DeltakerStatus,
    val historikk: List<DeltakerHistorikk>,
    val sistEndret: LocalDateTime = LocalDateTime.now(),
    val erManueltDeltMedArrangor: Boolean,
    val forcedUpdate: Boolean? = false,
)
