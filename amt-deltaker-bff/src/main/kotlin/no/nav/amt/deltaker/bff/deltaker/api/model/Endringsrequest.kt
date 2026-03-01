package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import java.util.UUID

sealed interface Endringsrequest {
    fun valider(deltaker: Deltaker)

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

sealed interface EndringsforslagRequest : Endringsrequest {
    val forslagId: UUID?
}
