package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.api.DtoMappers.opprettKladdResponseFromDeltaker
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.kladd.request.OpprettKladdRequest

fun Routing.registerKladdApi(pameldingService: PameldingService) {
    authenticate("SYSTEM") {
        post("/kladd") {
            // hvorfor opprettes kladd her og ikke oppdateres når det skjer endringer?
            val opprettKladdRequest = call.receive<OpprettKladdRequest>()

            val deltaker = pameldingService.opprettDeltaker(
                deltakerListeId = opprettKladdRequest.deltakerlisteId,
                personIdent = opprettKladdRequest.personident,
            )

            call.respond(opprettKladdResponseFromDeltaker(deltaker))
        }

        delete("/kladd/{deltakerId}") {
            pameldingService.slettKladd(call.getDeltakerId())
            call.respond(HttpStatusCode.OK)
        }
    }
}
