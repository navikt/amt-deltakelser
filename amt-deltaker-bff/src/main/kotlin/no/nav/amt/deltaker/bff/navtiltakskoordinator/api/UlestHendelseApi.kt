package no.nav.amt.deltaker.bff.navtiltakskoordinator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import java.util.UUID

fun Routing.registerUlestHendelseApi(ulestHendelseRepository: UlestHendelseRepository) {
    authenticate(AuthLevel.TILTAKSKOORDINATOR.name) {
        delete("/tiltakskoordinator/ulest-hendelse/{id}") {
            ulestHendelseRepository.delete(UUID.fromString(call.parameters["id"]))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
