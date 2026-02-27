package no.nav.amt.deltaker.deltaker.api.paamelding

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.api.DtoMappers.opprettKladdResponseFromDeltaker
import no.nav.amt.deltaker.deltaker.api.DtoMappers.utkastResponseFromDeltaker
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.OpprettKladdRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.UtkastRequest

fun Routing.registerPameldingApi(pameldingService: PameldingService, historikkService: DeltakerHistorikkService) {
    authenticate("SYSTEM") {
        // pamelding/kladd
        post("/pamelding") {
            val opprettKladdRequest = call.receive<OpprettKladdRequest>()

            val deltaker = pameldingService.opprettDeltaker(
                deltakerListeId = opprettKladdRequest.deltakerlisteId,
                personIdent = opprettKladdRequest.personident,
            )

            call.respond(opprettKladdResponseFromDeltaker(deltaker))
        }

        /*
            Kalles av av frontend via amt-deltaker-bff med:
            /pamelding/{deltakerId} godkjentAvNav=false
            /pamelding/{deltakerId}/utenGodkjenning godkjentAvNav=true
         */
        post("/pamelding/{deltakerId}") {
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

        post("/pamelding/{deltakerId}/innbygger/godkjenn-utkast") {
            val oppdatertDeltaker = pameldingService.innbyggerGodkjennUtkast(call.getDeltakerId())

            call.respond(
                utkastResponseFromDeltaker(
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

        delete("/pamelding/{deltakerId}") {
            pameldingService.slettKladd(call.getDeltakerId())
            call.respond(HttpStatusCode.OK)
        }
    }
}
