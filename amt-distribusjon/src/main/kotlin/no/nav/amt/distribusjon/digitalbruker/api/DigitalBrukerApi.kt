package no.nav.amt.distribusjon.digitalbruker.api

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.distribusjon.digitalbruker.DigitalBrukerService

fun Routing.registerDigitalBrukerApi(digitalBrukerService: DigitalBrukerService) {
    authenticate("SYSTEM") {
        post("/digital") {
            val request = call.receive<DigitalBrukerRequest>()
            call.respond(DigitalBrukerResponse(digitalBrukerService.erDigital(request.personident)))
        }
    }
}
