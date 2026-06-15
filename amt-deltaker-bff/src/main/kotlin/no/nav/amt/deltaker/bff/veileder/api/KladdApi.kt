package no.nav.amt.deltaker.bff.veileder.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.bff.application.plugins.getNavIdent
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.extensions.getDeltakerId
import no.nav.amt.deltaker.bff.veileder.api.request.OpprettKladdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.sanitize
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.internapi.paamelding.request.KladdRequest
import org.slf4j.LoggerFactory

fun Routing.registerKladdApi(
    tilgangskontrollService: TilgangskontrollService,
    amtDeltakerClient: AmtDeltakerClient,
    paameldingClient: PaameldingClient,
    paameldingService: PameldingService,
) {
    val log = LoggerFactory.getLogger(javaClass)

    authenticate(AuthLevel.VEILEDER.name) {
        post("/kladd") {
            val request = call.receive<OpprettKladdRequest>()

            tilgangskontrollService.verifiserSkrivetilgang(call.getNavAnsattAzureId(), request.personident)

            paameldingService
                .opprettKladd(request.deltakerlisteId, request.personident)
                .let(ModelMapper::toDeltaker)
                .let(DeltakerResponse::fromDeltakerModel)
                .let { call.respond(it) }
        }

        post("/kladd/{deltakerId}") {
            val request = call.receive<KladdRequest>().sanitize()
            val deltakerId = call.getDeltakerId()
            val norskIdent = amtDeltakerClient.getPersonidentForDeltaker(deltakerId)

            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = norskIdent,
            )
            paameldingClient.oppdaterKladd(deltakerId, request)

            call.respond(HttpStatusCode.OK)
        }

        delete("/kladd/{deltakerId}") {
            val deltakerId = call.getDeltakerId()

            tilgangskontrollService.verifiserSkrivetilgang(
                navAnsattAzureId = call.getNavAnsattAzureId(),
                norskIdent = amtDeltakerClient.getPersonidentForDeltaker(deltakerId),
            )

            paameldingClient.slettKladd(deltakerId)

            log.info("${call.getNavIdent()} har slettet kladd for deltaker med id $deltakerId")
            call.respond(HttpStatusCode.OK)
        }
    }
}
