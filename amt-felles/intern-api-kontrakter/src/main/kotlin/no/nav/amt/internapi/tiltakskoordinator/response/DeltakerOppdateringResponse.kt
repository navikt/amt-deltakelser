package no.nav.amt.internapi.tiltakskoordinator.response

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerOppdateringResponse(
    val id: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val status: DeltakerStatus,
    val sistEndret: LocalDateTime = LocalDateTime.now(),
    val erManueltDeltMedArrangor: Boolean,
    val feilkode: DeltakerOppdateringFeilkode?,
)
