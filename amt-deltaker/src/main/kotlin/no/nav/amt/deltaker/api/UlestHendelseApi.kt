package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import java.util.UUID

fun Routing.registerUlestHendelseApi(ulestHendelseRepository: UlestHendelseRepository) {
    authenticate("SYSTEM") {
        route("/tiltakskoordinator/ulest-hendelse") {
            get("/{deltakerId}") {
                val deltakerId = call.parameters["deltakerId"]?.let(UUID::fromString)
                    ?: throw IllegalArgumentException("Påkrevd URL parameter 'deltakerId' mangler.")
                call.respond(ulestHendelseRepository.getForDeltaker(deltakerId))
            }

            post("/deltakere") {
                val deltakerIder = call.receive<List<UUID>>().toSet()
                call.respond(ulestHendelseRepository.getForDeltakere(deltakerIder))
            }

            post("/type-counts") {
                val deltakerIder = call.receive<List<UUID>>().toSet()
                call.respond(ulestHendelseRepository.getTypeCountsForDeltakere(deltakerIder))
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.let(UUID::fromString)
                    ?: throw IllegalArgumentException("Påkrevd URL parameter 'id' mangler.")
                ulestHendelseRepository.delete(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private data class UpsertUlesteHendelserResponse(
    val upserted: Int,
)
