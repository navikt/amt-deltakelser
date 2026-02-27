package no.nav.amt.deltaker.bff.testdata

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel

// Brukes av team tiltakspenger for å lage testdata for manuell testing i dev.
// På sikt kan dette kanskje tilbys til Dolly.
fun Routing.registerTestdataApi(testdataService: TestdataService) {
    authenticate(AuthLevel.SYSTEM.name) {
        post("/testdata/opprett") {
            val request = call.receive<OpprettTestDeltakelseRequest>()
            request.valider()
            val deltaker = testdataService.opprettDeltakelse(request)

            call.respond(deltaker)
        }
    }
}
