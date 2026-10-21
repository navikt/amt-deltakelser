package no.nav.amt.deltaker.bff.extensions

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.veileder.api.request.EndringRequestFromFrontend
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.request.EndringRequest
import java.util.UUID

private const val DELTAKER_ID_PARAM = "deltakerId"
private const val FORSLAG_ID_PARAM = "forslagId"
private const val AKTIV_ENHET_HEADER = "aktiv-enhet"
private const val TERMS_PARAM = "term"

// for arrangørsøk og sertifiseringssøk
fun ApplicationCall.getTerm(): String = this.parameters[TERMS_PARAM] ?: throw IllegalArgumentException("Mangler søketerm")

fun ApplicationCall.getDeltakerId(): UUID = UUID.fromString(this.parameters[DELTAKER_ID_PARAM])

fun ApplicationCall.getForslagId(): UUID = UUID.fromString(this.parameters[FORSLAG_ID_PARAM])

fun ApplicationCall.getEnhetsnummer() = this.request.headerNotNull(AKTIV_ENHET_HEADER)

suspend fun ApplicationCall.handleEndringRequest(
    frontendRequest: EndringRequestFromFrontend,
    amtDeltakerRequest: EndringRequest,
    amtDeltakerClient: AmtDeltakerClient,
    tilgangskontrollService: TilgangskontrollService,
) {
    val deltakerId = this.getDeltakerId()

    val deltaker = amtDeltakerClient
        .getDeltaker(deltakerId)
        .let { ModelMapper.toDeltaker(it) }

    tilgangskontrollService.verifiserSkrivetilgang(
        navAnsattAzureId = this.getNavAnsattAzureId(),
        norskIdent = deltaker.navBruker.personident,
    )
    frontendRequest.valider(deltaker)

    amtDeltakerClient
        .postEndreDeltaker(
            deltakerId = deltaker.id,
            requestBody = amtDeltakerRequest,
        ).let { ModelMapper.toDeltaker(it) }
        .let { DeltakerResponse.fromDeltakerModel(it) }
        .also { this.respond(it) }
}
