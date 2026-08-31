package no.nav.amt.internapi.deltaker.request

import com.fasterxml.jackson.annotation.JsonTypeInfo

/*
    Endringsrequest er en dto for å kommunisere alle endringer som kan gjøres på en deltaker
    fra amt-deltaker-bff til amt-deltaker
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringRequest {
    val endretAv: String
    val endretAvEnhet: String

    fun kanIverksettesUtenAktivOppfolging() = when (this) {
        is EndretPrisinfoRequest,
        is EndretOpplaringKategoriseringRequest,
        is BakgrunnsinformasjonRequest,
        is DeltakelsesmengdeRequest,
        is EndretInnholdRequest,
        is StartdatoRequest,
        is ForlengDeltakelseRequest,
        is ReaktiverDeltakelseRequest,
        is FjernOppstartsdatoRequest,
        -> false

        is AvsluttDeltakelseRequest,
        is AvbrytDeltakelseRequest,
        is EndreAvslutningRequest,
        is SluttarsakRequest,
        is SluttdatoRequest,
        is IkkeAktuellRequest,
        -> true
    }
}
