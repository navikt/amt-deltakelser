package no.nav.amt.internapi.tiltakskoordinator.response

data class DeltakerOppdateringResponse(
    val deltaker: TiltakskoordinatorDeltakerIListeResponse,
    val feilkode: DeltakerOppdateringFeilkode?,
)
