package no.nav.amt.deltaker.bff.navtiltakskoordinator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorClient
import java.util.UUID

fun Routing.registerUlestHendelseApi(tiltakskoordinatorClient: TiltakskoordinatorClient) {
    authenticate(AuthLevel.TILTAKSKOORDINATOR.name) {
        delete("/tiltakskoordinator/ulest-hendelse/{id}") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: throw IllegalArgumentException("Påkrevd URL parameter 'id' mangler.")
            tiltakskoordinatorClient.slettUlestHendelse(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
