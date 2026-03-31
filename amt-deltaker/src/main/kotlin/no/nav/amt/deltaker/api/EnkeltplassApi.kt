package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.enkeltplass.MeldPaaDirekteEnkeltplassRequest
import no.nav.amt.internapi.paamelding.request.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.paamelding.request.OpprettKladdEnkeltplassRequest

fun Routing.registerEnkeltplassApi(enkeltplassService: EnkeltplassService) {
    authenticate("SYSTEM") {
        route("/enkeltplass") {
            post("/opprett-kladd") {
                val opprettKladdRequest = call.receive<OpprettKladdEnkeltplassRequest>()

                val deltaker = enkeltplassService
                    .opprettKladd(opprettKladdRequest.tiltakskode, opprettKladdRequest.personident)

                call.respond(DeltakerIdResponse(deltakerId = deltaker.id))
            }

            post("/oppdater-kladd/{deltakerId}") {
                val deltakerId = call.getDeltakerId()
                val oppdaterKladdRequest = call.receive<OppdaterEnkeltplassKladdRequest>()
                enkeltplassService
                    .oppdaterKladd(
                        deltakerId = deltakerId,
                        startdato = oppdaterKladdRequest.startdato,
                        sluttdato = oppdaterKladdRequest.sluttdato,
                        prisinformasjon = oppdaterKladdRequest.prisinformasjon,
                        beskrivelse = oppdaterKladdRequest.beskrivelse,
                    )

                call.respond(HttpStatusCode.OK)
            }

            post("/utkast/{deltakerId}/meld-paa-direkte") {
                val request: MeldPaaDirekteEnkeltplassRequest = call.receive()

                enkeltplassService.meldPaaDirekte(
                    deltakerId = call.getDeltakerId(),
                    request = request,
                )

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
