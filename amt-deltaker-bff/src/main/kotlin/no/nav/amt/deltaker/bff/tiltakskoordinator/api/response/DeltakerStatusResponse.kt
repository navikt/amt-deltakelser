package no.nav.amt.deltaker.bff.tiltakskoordinator.api.response
import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class DeltakerStatusResponse(
    val type: DeltakerStatus.Type,
    val aarsak: DeltakerStatusAarsakResponse?,
)
