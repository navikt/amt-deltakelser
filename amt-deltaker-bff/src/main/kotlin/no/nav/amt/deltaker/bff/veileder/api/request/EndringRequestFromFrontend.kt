package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import java.util.UUID

sealed interface EndringRequestFromFrontend {
    fun valider(deltaker: Deltaker)

    fun tillattEndringUtenAktivOppfolgingsperiode() = when (this) {
        is EndreBakgrunnsinformasjonRequest,
        is EndreDeltakelsesmengdeRequest,
        is EndreInnholdRequestFromFrontend,
        is EndreStartdatoRequest,
        is ForlengDeltakelseRequest,
        is ReaktiverDeltakelseRequestFromFrontend,
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
