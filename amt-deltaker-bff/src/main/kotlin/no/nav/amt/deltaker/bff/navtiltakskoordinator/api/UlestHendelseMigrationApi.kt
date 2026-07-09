package no.nav.amt.deltaker.bff.navtiltakskoordinator.api

import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorClient
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository

fun Routing.registerUlestHendelseMigrationApi(
    ulestHendelseRepository: UlestHendelseRepository,
    tiltakskoordinatorClient: TiltakskoordinatorClient,
) {
    authenticate(AuthLevel.SYSTEM.name) {
        post("/internal/tiltakskoordinator/ulest-hendelse/sync") {
            val fom = call.request.queryParameters["fom"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Påkrevd query-parameter 'fom' mangler eller er ugyldig.")
            val tom = call.request.queryParameters["tom"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Påkrevd query-parameter 'tom' mangler eller er ugyldig.")

            require(fom >= 0) { "Query-parameter 'fom' må være >= 0." }
            require(tom >= fom) { "Query-parameter 'tom' må være >= 'fom'." }

            val ulesteHendelser = ulestHendelseRepository.getRangeOrderedByOpprettet(
                offset = fom,
                limit = tom - fom + 1,
            )

            val upserted = tiltakskoordinatorClient.upsertUlesteHendelser(ulesteHendelser)

            call.respond(
                UlestHendelseSyncResponse(
                    fom = fom,
                    tom = tom,
                    hentet = ulesteHendelser.size,
                    upserted = upserted,
                ),
            )
        }
    }
}

private data class UlestHendelseSyncResponse(
    val fom: Int,
    val tom: Int,
    val hentet: Int,
    val upserted: Int,
)
