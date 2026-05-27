import aooppfolgingskontor.aoOppfolgingskontorFakeRoutes
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
        veilarbvedtaksstotteFakeRoutes()
        bronnoysundFakeRoutes()
        norgFakeRoutes()
        aoOppfolgingskontorFakeRoutes()
        pdlFakeRoutes()
        nomFakeRoutes()
        krrProxyFakeRoutes()
        kafkaFakeRoutes(kafkaPublisher)

        // Keep previous behavior for unknown paths.
        route("{...}") {
            handle {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"not found\"}")
            }
        }
    }
}

