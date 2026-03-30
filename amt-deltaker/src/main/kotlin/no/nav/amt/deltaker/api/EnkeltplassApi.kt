package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.extensions.getDeltakerId

fun Routing.registerEnkeltplassApi(enkeltplassService: EnkeltplassService) {
    authenticate("SYSTEM") {
        post("/enkeltplass-utkast/{deltakerId}/meld-paa-direkte") {
            enkeltplassService.opprettGjennomforingRemote(call.getDeltakerId())
            call.respond(HttpStatusCode.OK)
        }
    }
}
