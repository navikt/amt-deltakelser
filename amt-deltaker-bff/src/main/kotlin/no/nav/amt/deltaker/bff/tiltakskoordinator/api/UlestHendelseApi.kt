package no.nav.amt.deltaker.bff.tiltakskoordinator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.UlestHendelseService
import java.util.UUID

fun Routing.registerUlestHendelseApi(ulestHendelseService: UlestHendelseService) {
    val apiPath = "/tiltakskoordinator/ulest-hendelse/{id}"

    authenticate(AuthLevel.TILTAKSKOORDINATOR.name) {
        delete(apiPath) {
            ulestHendelseService.delete(UUID.fromString(call.parameters["id"]))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
