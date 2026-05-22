package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.DeltakerModel
import java.util.UUID

sealed interface EndringRequestFromFrontend {
    fun valider(deltaker: Deltaker)

    fun valider(deltaker: DeltakerModel)

    /**
     * Returnerer true dersom endringen er tillatt for en deltaker som er låst for endringer
     * (det finnes en nyere deltakelse på samme tiltak), men har fått avsluttende status
     * for under 2 måneder siden.
     */
    fun tillattForLaastAvsluttetDeltakelse() = when (this) {
        is EndreAvslutningRequest,
        is EndreSluttarsakRequest,
        -> true

        else -> false
    }

    fun tillattEndringUtenAktivOppfolgingsperiode() = when (this) {
        is EndreBakgrunnsinformasjonRequest,
        is EndreDeltakelsesmengdeRequest,
        is EndreInnholdRequest,
        is EndreStartdatoRequest,
        is ForlengDeltakelseRequest,
        is ReaktiverDeltakelseRequest,
        is FjernOppstartsdatoRequest,
        -> false

        is AvsluttDeltakelseRequest,
        is EndreAvslutningRequest,
        is EndreSluttarsakRequest,
        is EndreSluttdatoRequest,
        is IkkeAktuellRequest,
        -> true
    }
}

sealed interface EndringMedForslagRequest : EndringRequestFromFrontend {
    val forslagId: UUID?
}
