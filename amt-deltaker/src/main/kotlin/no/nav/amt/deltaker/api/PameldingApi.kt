package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.api.response.SharedResponseMappers.utkastResponseFromDeltaker
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.veileder.PameldingService
import no.nav.amt.internapi.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.internapi.paamelding.request.UtkastRequest

fun Routing.registerPameldingApi(
    pameldingService: PameldingService,
    historikkService: DeltakerHistorikkService,
) {
    authenticate("SYSTEM") {
        route("/pamelding") {
            /*
                Kalles av av frontend via amt-deltaker-bff med:
                /pamelding/{deltakerId} godkjentAvNav=false (opprett/oppdater utkast)
                /pamelding/{deltakerId}/utenGodkjenning godkjentAvNav=true(meld på uten å dele utkast)

                sånn sett så kan dette kalles "godkjenn påmelding"

             */
            post("/{deltakerId}") {
                val deltaker = pameldingService.upsertUtkast(
                    deltakerId = call.getDeltakerId(),
                    utkast = call.receive<UtkastRequest>(),
                )

                call.respond(
                    utkastResponseFromDeltaker(
                        deltaker = deltaker,
                        historikk = historikkService.getForDeltaker(deltaker.id),
                    ),
                )
            }

            post("/{deltakerId}/innbygger/godkjenn-utkast") {
                val oppdatertDeltaker = pameldingService.innbyggerGodkjennUtkast(call.getDeltakerId())

                call.respond(
                    utkastResponseFromDeltaker(
                        deltaker = oppdatertDeltaker,
                        historikk = historikkService.getForDeltaker(oppdatertDeltaker.id),
                    ),
                )
            }

            post("/{deltakerId}/avbryt") {
                pameldingService.avbrytUtkast(
                    deltakerId = call.getDeltakerId(),
                    avbrytUtkastRequest = call.receive<AvbrytUtkastRequest>(),
                )

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
