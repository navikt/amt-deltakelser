package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.extensions.getDeltakerId
import no.nav.amt.internapi.enkeltplass.MeldPaaDirekteEnkeltplassRequest

fun Routing.registerEnkeltplassApi(enkeltplassService: EnkeltplassService) {
    authenticate("SYSTEM") {
        post("/enkeltplass-utkast/{deltakerId}/meld-paa-direkte") {
            val request: MeldPaaDirekteEnkeltplassRequest = call.receive()

            enkeltplassService.opprettGjennomforingRemote(
                deltakerId = call.getDeltakerId(),
                request = request,
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}
