import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.simNavModule(kafkaPublisher: KafkaPublisher) {
    routing {
        unleashFakeRoutes()
        poaoTilgangFakeRoutes()
        bronnoysundFakeRoutes()
        kafkaFakeRoutes(kafkaPublisher)

        // Keep previous behavior for unknown paths.
        route("{...}") {
            handle {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"not found\"}")
            }
        }
    }
}

