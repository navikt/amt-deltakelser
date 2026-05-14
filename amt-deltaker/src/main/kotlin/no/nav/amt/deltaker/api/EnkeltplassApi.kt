package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.api.response.ResponseBuilder
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.enkeltplass.OpprettKladdEnkeltplassRequest

fun Routing.registerEnkeltplassApi(
    enkeltplassService: EnkeltplassService,
    responseBuilder: ResponseBuilder,
) {
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

                enkeltplassService.oppdaterKladd(
                    deltakerId = deltakerId,
                    oppdaterKladdRequest = oppdaterKladdRequest.sanitized(),
                )

                call.respond(HttpStatusCode.OK)
            }

            post("/utkast/{deltakerId}") {
                val request: EnkeltplassPameldingDecoratedRequest = call.receive()

                val oppdatertDeltaker = enkeltplassService.oppdaterUtkast(
                    deltakerId = call.getDeltakerId(),
                    decoratedRequest = request,
                )

                val deltakerResponse = responseBuilder.buildDeltakerResponse(
                    deltaker = oppdatertDeltaker,
                    includeKodeverk = true,
                )

                call.respond(deltakerResponse)
            }

            /*
                Del utkast med innbygger
                Handling: Del utkast
                Status: Kladd-> Utkast
             */
            post("/utkast/{deltakerId}/del-med-innbygger") {
                val request: EnkeltplassPameldingDecoratedRequest = call.receive()

                val oppdatertDeltaker = enkeltplassService.delUtkastMedInnbygger(
                    deltakerId = call.getDeltakerId(),
                    decoratedRequest = request,
                )

                val deltakerResponse = responseBuilder.buildDeltakerResponse(
                    deltaker = oppdatertDeltaker,
                    includeKodeverk = true,
                )

                call.respond(deltakerResponse)
            }

            post("/utkast/{deltakerId}/meld-paa-direkte") {
                val request: EnkeltplassPameldingDecoratedRequest = call.receive()

                enkeltplassService.meldPaaDirekte(
                    deltakerId = call.getDeltakerId(),
                    decoratedRequest = request,
                )

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
