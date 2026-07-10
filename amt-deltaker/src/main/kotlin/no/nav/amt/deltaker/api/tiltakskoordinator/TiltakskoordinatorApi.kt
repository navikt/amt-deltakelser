package no.nav.amt.deltaker.api.tiltakskoordinator

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.api.response.TiltakskoordinatorResponseBuilder
import no.nav.amt.deltaker.extensions.getGjennomforingId
import no.nav.amt.deltaker.navtiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.internapi.tiltakskoordinator.request.DeltakereRequest
import no.nav.amt.internapi.tiltakskoordinator.request.GiAvslagRequest
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest

fun Routing.registerTiltakskoordinatorApi(
    deltakerlisteRepository: DeltakerlisteRepository,
    deltakerResponseBuilder: DeltakerResponseBuilder,
    tiltakskoordinatorService: TiltakskoordinatorService,
    tiltakskoordinatorResponseBuilder: TiltakskoordinatorResponseBuilder,
) {
    authenticate("SYSTEM") {
        get("/gjennomforing/{gjennomforingId}") {
            val gjennomforingId = call.getGjennomforingId()
            val gjennomforingResponse = deltakerlisteRepository
                .get(gjennomforingId)
                .getOrThrow()
                .let {
                    deltakerResponseBuilder.buildGjennomforingResponse(
                        deltakerliste = it,
                        includeOpplaringKategorisering = false,
                    )
                }

            call.respond(gjennomforingResponse)
        }

        route("/tiltakskoordinator/deltakere") {
            post("/{gjennomforingId}") {
                val gjennomforingId = call.getGjennomforingId()
                val request = call.receive<TiltaksKoordinatorDeltakerlisteRequest>()
                require(request.gjennomforingId == gjennomforingId) {
                    "GjennomforingId i path matcher ikke gjennomforingId i request body"
                }
                call.respond(tiltakskoordinatorResponseBuilder.buildResponse(request))
            }

            post("/status-counts") {
                val request = call.receive<TiltaksKoordinatorDeltakerlisteRequest>()
                require(request.statuser.isNotEmpty()) {
                    "Statuser må spesifiseres for å hente deltakerantall per status"
                }

                call.respond(tiltakskoordinatorResponseBuilder.buildStatusCountsResponse(request))
            }

            post("/del-med-arrangor") {
                val request = call.receive<DelMedArrangorRequest>()

                val deltakeroppdateringer = tiltakskoordinatorService
                    .oppdaterDeltakere(
                        gjennomforingId = request.gjennomforingId,
                        deltakerIder = request.deltakerIder.toSet(),
                        endringsType = EndringFraTiltakskoordinator.DelMedArrangor,
                        endretAvIdent = request.endretAv,
                    )

                val response = tiltakskoordinatorResponseBuilder.buildResponse(
                    request.gjennomforingId,
                    request.deltakerIder,
                    deltakeroppdateringer.associate { it.deltakerId to it.exception },
                )
                call.respond(response)
            }

            post("/tildel-plass") {
                val request = call.receive<DeltakereRequest>()
                val deltakerIder = request.deltakere
                val deltakeroppdateringer = tiltakskoordinatorService
                    .oppdaterDeltakere(
                        gjennomforingId = request.gjennomforingId,
                        deltakerIder = deltakerIder.toSet(),
                        endringsType = EndringFraTiltakskoordinator.TildelPlass,
                        endretAvIdent = request.endretAv,
                    )
                val response = tiltakskoordinatorResponseBuilder.buildResponse(
                    gjennomforingId = request.gjennomforingId,
                    deltakerIder = request.deltakere,
                    deltakeroppdateringer.associate { it.deltakerId to it.exception },
                )
                call.respond(response)
            }

            post("/sett-paa-venteliste") {
                val request = call.receive<DeltakereRequest>()
                val deltakerIder = request.deltakere
                val deltakeroppdateringer = tiltakskoordinatorService
                    .oppdaterDeltakere(
                        gjennomforingId = request.gjennomforingId,
                        deltakerIder = deltakerIder.toSet(),
                        endringsType = EndringFraTiltakskoordinator.SettPaaVenteliste,
                        endretAvIdent = request.endretAv,
                    )
                val response = tiltakskoordinatorResponseBuilder.buildResponse(
                    gjennomforingId = request.gjennomforingId,
                    deltakerIder = request.deltakere,
                    deltakeroppdateringer.associate { it.deltakerId to it.exception },
                )
                call.respond(response)
            }

            post("/gi-avslag") {
                val request = call.receive<GiAvslagRequest>()
                val deltakeroppdatering = tiltakskoordinatorService
                    .giAvslag(
                        gjennomforingId = request.gjennomforingId,
                        deltakerId = request.deltakerId,
                        avslag = request.avslag,
                        endretAv = request.endretAv,
                    )
                val response = tiltakskoordinatorResponseBuilder
                    .buildResponse(
                        gjennomforingId = request.gjennomforingId,
                        deltakerId = request.deltakerId,
                        exception = deltakeroppdatering.exception,
                    )
                call.respond(response)
            }
        }
    }
}
