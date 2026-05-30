package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.deltaker.veileder.PameldingService
import no.nav.amt.internapi.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.internapi.paamelding.request.UtkastRequest

fun Routing.registerPameldingApi(
    pameldingService: PameldingService,
    deltakerResponseBuilder: DeltakerResponseBuilder,
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
                pameldingService
                    .upsertUtkast(
                        deltakerId = call.getDeltakerId(),
                        utkast = call.receive<UtkastRequest>(),
                    ).let { deltakerResponseBuilder.buildDeltakerResponse(it) }
                    .let { call.respond(it) }
            }

            post("/{deltakerId}/innbygger/godkjenn-utkast") {
                pameldingService
                    .innbyggerGodkjennUtkast(call.getDeltakerId())
                    .let { deltakerResponseBuilder.buildDeltakerResponse(it) }
                    .also { call.respond(it) }
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
