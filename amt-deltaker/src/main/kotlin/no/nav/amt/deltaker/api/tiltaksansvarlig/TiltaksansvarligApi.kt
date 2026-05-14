package no.nav.amt.deltaker.api.tiltaksansvarlig

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.api.response.ResponseBuilder
import no.nav.amt.deltaker.api.tiltaksansvarlig.ResponseMapper.toDeltakerOppdatering
import no.nav.amt.deltaker.extensions.getGjennomforingId
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksansvarlig.TiltaksansvarligService
import no.nav.amt.internapi.deltaker.response.DeltakereResponse
import no.nav.amt.internapi.tiltakskoordinator.request.DeltakereRequest
import no.nav.amt.internapi.tiltakskoordinator.request.GiAvslagRequest
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest

fun Routing.registerTiltakskoordinatorApi(
    tiltaksansvarligService: TiltaksansvarligService,
    deltakerHistorikkService: DeltakerHistorikkService,
    deltakerRepository: DeltakerRepository,
    responseBuilder: ResponseBuilder,
) {
    fun List<DeltakerOppdateringResult>.toDeltakerOppdateringResult() = this.map {
        ResponseMapper.fromDeltakerOppdateringResult(
            oppdateringResult = it,
            historikk = deltakerHistorikkService.getForDeltaker(it.deltaker.id),
        )
    }

    authenticate("SYSTEM") {
        route("/tiltakskoordinator/deltakere") {
            get("/{gjennomforingId}") {
                val deltakereResponse = deltakerRepository
                    .getForGjennomforing(gjennomforingId = call.getGjennomforingId())
                    .let { deltakere ->
                        DeltakereResponse(
                            deltakere.map { deltaker ->
                                responseBuilder.buildDeltakerResponse(deltaker)
                            },
                        )
                    }

                call.respond(deltakereResponse)
            }

            post("/del-med-arrangor") {
                val request = call.receive<DelMedArrangorRequest>()

                val oppdaterteDeltakere = tiltaksansvarligService
                    .oppdaterDeltakere(
                        request.deltakerIder.toSet(),
                        EndringFraTiltakskoordinator.DelMedArrangor,
                        request.endretAv,
                    ).toDeltakerOppdateringResult()
                call.respond(oppdaterteDeltakere)
            }

            post("/tildel-plass") {
                val request = call.receive<DeltakereRequest>()
                val deltakerIder = request.deltakere
                val oppdaterteDeltakere = tiltaksansvarligService
                    .oppdaterDeltakere(
                        deltakerIder.toSet(),
                        EndringFraTiltakskoordinator.TildelPlass,
                        request.endretAv,
                    ).toDeltakerOppdateringResult()

                call.respond(oppdaterteDeltakere)
            }

            post("/sett-paa-venteliste") {
                val request = call.receive<DeltakereRequest>()
                val deltakerIder = request.deltakere
                val oppdaterteDeltakere = tiltaksansvarligService
                    .oppdaterDeltakere(
                        deltakerIder.toSet(),
                        EndringFraTiltakskoordinator.SettPaaVenteliste,
                        request.endretAv,
                    ).toDeltakerOppdateringResult()

                call.respond(oppdaterteDeltakere)
            }

            post("/gi-avslag") {
                val request = call.receive<GiAvslagRequest>()
                val deltakeroppdatering = tiltaksansvarligService
                    .giAvslag(
                        request.deltakerId,
                        request.avslag,
                        request.endretAv,
                    ).toDeltakerOppdatering(deltakerHistorikkService.getForDeltaker(request.deltakerId))

                call.respond(deltakeroppdatering)
            }
        }
    }
}
