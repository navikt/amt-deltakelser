package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.KladdService
import no.nav.amt.deltaker.deltaker.api.DtoMappers.opprettKladdResponseFromDeltaker
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.paamelding.request.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.paamelding.request.OpprettKladdEnkeltplassRequest
import no.nav.amt.internapi.paamelding.request.OpprettKladdRequest

fun Routing.registerKladdApi(kladdService: KladdService) {
    authenticate("SYSTEM") {
        post("/kladd") {
            val opprettKladdRequest = call.receive<OpprettKladdRequest>()

            val deltaker = kladdService.opprettKladd(
                deltakerListeId = opprettKladdRequest.deltakerlisteId,
                personIdent = opprettKladdRequest.personident,
            )

            call.respond(opprettKladdResponseFromDeltaker(deltaker))
        }

        post("/opprett-enkeltplass-kladd") {
            val opprettKladdRequest = call.receive<OpprettKladdEnkeltplassRequest>()

            val deltaker = kladdService
                .opprettKladd(opprettKladdRequest.tiltakskode, opprettKladdRequest.personident)

            call.respond(DeltakerIdResponse(deltakerId = deltaker.id))
        }

        post("/oppdater-enkeltplass-kladd/{deltakerId}") {
            val deltakerId = call.getDeltakerId()
            val oppdaterKladdRequest = call.receive<OppdaterEnkeltplassKladdRequest>()
            kladdService
                .oppdaterKladd(
                    deltakerId = deltakerId,
                    startdato = oppdaterKladdRequest.startdato,
                    sluttdato = oppdaterKladdRequest.sluttdato,
                    prisinformasjon = oppdaterKladdRequest.prisinformasjon,
                    beskrivelse = oppdaterKladdRequest.beskrivelse,
                )

            call.respond(HttpStatusCode.OK)
        }

        delete("/kladd/{deltakerId}") {
            kladdService.slettKladd(call.getDeltakerId())
            call.respond(HttpStatusCode.OK)
        }
    }
}
