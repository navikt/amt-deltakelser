import aooppfolgingskontor.aoOppfolgingskontorFakeRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kafka.KafkaPublisher
import kafka.kafkaFakeRoutes
import nom.nomFakeRoutes
import pdl.pdlFakeRoutes

fun Application.simNavModule(kafkaPublisher: KafkaPublisher, bronnoysundSimulator: BronnoysundSimulator) {
    routing {
        unleashFakeRoutes()
        poaoTilgangFakeRoutes()
        veilarboppfolgingFakeRoutes()
        veilarbvedtaksstotteFakeRoutes()
        bronnoysundFakeRoutes(bronnoysundSimulator)
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

