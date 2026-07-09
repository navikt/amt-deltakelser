package no.nav.amt.deltaker.bff.navtiltakskoordinator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorClient
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import org.slf4j.LoggerFactory
import java.util.UUID

fun Routing.registerUlestHendelseApi(
    ulestHendelseRepository: UlestHendelseRepository,
    tiltakskoordinatorClient: TiltakskoordinatorClient,
) {
    authenticate(AuthLevel.TILTAKSKOORDINATOR.name) {
        delete("/tiltakskoordinator/ulest-hendelse/{id}") {
            val id = UUID.fromString(call.parameters["id"])
            runCatching { tiltakskoordinatorClient.slettUlestHendelse(id) }
                .onFailure { e ->
                    log.warn("Klarte ikke å slette ulest hendelse {} i amt-deltaker, fortsetter med lokal sletting", id, e)
                }
            ulestHendelseRepository.delete(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private val log = LoggerFactory.getLogger("UlestHendelseApi")
