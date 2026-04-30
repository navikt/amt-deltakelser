package no.nav.amt.deltaker.api.tiltaksansvarlig

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.api.tiltaksansvarlig.ResponseMapper.toDeltakerOppdatering
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksansvarlig.TiltaksansvarligService
import no.nav.amt.internapi.tiltakskoordinator.request.DeltakereRequest
import no.nav.amt.internapi.tiltakskoordinator.request.GiAvslagRequest
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest

fun Routing.registerTiltakskoordinatorApi(
    tiltaksansvarligService: TiltaksansvarligService,
    deltakerHistorikkService: DeltakerHistorikkService,
) {
    val apiPath = "/tiltakskoordinator/deltakere"

    fun List<DeltakerOppdateringResult>.toDeltakerOppdateringResult() = this.map {
        ResponseMapper.fromDeltakerOppdateringResult(
            oppdateringResult = it,
            historikk = deltakerHistorikkService.getForDeltaker(it.deltaker.id),
        )
    }

    authenticate("SYSTEM") {
        post("$apiPath/del-med-arrangor") {
            val request = call.receive<DelMedArrangorRequest>()

            val oppdaterteDeltakere = tiltaksansvarligService
                .oppdaterDeltakere(
                    request.deltakerIder.toSet(),
                    EndringFraTiltakskoordinator.DelMedArrangor,
                    request.endretAv,
                ).toDeltakerOppdateringResult()
            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/tildel-plass") {
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

        post("$apiPath/sett-paa-venteliste") {
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

        post("$apiPath/gi-avslag") {
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
