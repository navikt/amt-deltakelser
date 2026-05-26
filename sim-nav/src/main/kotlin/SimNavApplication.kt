import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import nom.nomFakeRoutes
import pdl.pdlFakeRoutes

fun Application.simNavModule(kafkaPublisher: KafkaPublisher) {
    routing {
        unleashFakeRoutes()
        poaoTilgangFakeRoutes()
        veilarboppfolgingFakeRoutes()
        bronnoysundFakeRoutes()
        pdlFakeRoutes()
        nomFakeRoutes()
        kafkaFakeRoutes(kafkaPublisher)

        // Keep previous behavior for unknown paths.
        route("{...}") {
            handle {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"not found\"}")
            }
        }
    }
}

