package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.api.response.ResponseMapper
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
        /*
            Kalles av av frontend via amt-deltaker-bff med:
            /pamelding/{deltakerId} godkjentAvNav=false (opprett/oppdater utkast)
            /pamelding/{deltakerId}/utenGodkjenning godkjentAvNav=true(meld på uten å dele utkast)

            sånn sett så kan dette kalles "godkjenn påmelding"

         */
        post("/pamelding/{deltakerId}") {
            val deltaker = pameldingService.upsertUtkast(
                deltakerId = call.getDeltakerId(),
                utkast = call.receive<UtkastRequest>(),
            )

            call.respond(
                ResponseMapper.utkastResponseFromDeltaker(
                    deltaker = deltaker,
                    historikk = historikkService.getForDeltaker(deltaker.id),
                ),
            )
        }

        post("/pamelding/{deltakerId}/innbygger/godkjenn-utkast") {
            val oppdatertDeltaker = pameldingService.innbyggerGodkjennUtkast(call.getDeltakerId())

            call.respond(
                ResponseMapper.utkastResponseFromDeltaker(
                    deltaker = oppdatertDeltaker,
                    historikk = historikkService.getForDeltaker(oppdatertDeltaker.id),
                ),
            )
        }

        post("/pamelding/{deltakerId}/avbryt") {
            pameldingService.avbrytUtkast(
                deltakerId = call.getDeltakerId(),
                avbrytUtkastRequest = call.receive<AvbrytUtkastRequest>(),
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}
