package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.enkeltplass.GjennomforingUpserter
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassTilbakekallPrisinfoRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.enkeltplass.OpprettKladdEnkeltplassRequest

fun Routing.registerEnkeltplassApi(
    enkeltplassService: EnkeltplassService,
    deltakerResponseBuilder: DeltakerResponseBuilder,
    gjennomforingUpserter: GjennomforingUpserter,
) {
    authenticate("SYSTEM") {
        route("/enkeltplass") {
            post("/opprett-kladd") {
                val opprettKladdRequest = call.receive<OpprettKladdEnkeltplassRequest>()

                val deltaker = enkeltplassService.opprettKladd(
                    tiltakskode = opprettKladdRequest.tiltakskode,
                    personident = opprettKladdRequest.personident,
                )

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

            route("/utkast/{deltakerId}") {
                post {
                    val request: EnkeltplassPameldingDecoratedRequest = call.receive()

                    val oppdatertDeltaker = enkeltplassService.oppdaterUtkast(
                        deltakerId = call.getDeltakerId(),
                        decoratedRequest = request,
                    )

                    val deltakerResponse = deltakerResponseBuilder.buildDeltakerResponse(
                        deltaker = oppdatertDeltaker,
                    )

                    call.respond(deltakerResponse)
                }

                /*
                    Del utkast med innbygger
                    Handling: Del utkast
                    Status: Kladd-> Utkast
                 */
                post("/del-med-innbygger") {
                    val request: EnkeltplassPameldingDecoratedRequest = call.receive()

                    val oppdatertDeltaker = enkeltplassService.delUtkastMedInnbygger(
                        deltakerId = call.getDeltakerId(),
                        decoratedRequest = request,
                    )

                    val deltakerResponse = deltakerResponseBuilder.buildDeltakerResponse(oppdatertDeltaker)

                    call.respond(deltakerResponse)
                }

                post("/meld-paa-direkte") {
                    val request: EnkeltplassPameldingDecoratedRequest = call.receive()

                    enkeltplassService.meldPaaDirekte(
                        deltakerId = call.getDeltakerId(),
                        decoratedRequest = request,
                    )

                    call.respond(HttpStatusCode.OK)
                }
            }

            post("/tilbakekall-prisendring/{deltakerId}") {
                val request: EnkeltplassTilbakekallPrisinfoRequest = call.receive()

                gjennomforingUpserter.produserTilbakekallPrisendring(
                    call.getDeltakerId(),
                    request.endretAv,
                )

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
