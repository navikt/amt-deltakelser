import aooppfolgingskontor.aoOppfolgingskontorFakeRoutes
import brreg.BronnoysundSimulator
import brreg.bronnoysundFakeRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kafka.KafkaPublisher
import kafka.kafkaFakeRoutes
import nom.nomFakeRoutes
import pdl.pdlFakeRoutes

fun Application.simNavModule(
    kafkaPublisher: KafkaPublisher,
    bronnoysundSimulator: BronnoysundSimulator,
    pdlSimulator: pdl.PdlSimulator,
    norgSimulator: NorgSimulator,
) {
    // install(RequestDebugPlugin)

    routing {
        altinn3FakeRoutes()
        maskinportenFakeRoutes()
        unleashFakeRoutes()
        poaoTilgangFakeRoutes()
        veilarboppfolgingFakeRoutes()
        veilarbvedtaksstotteFakeRoutes()
        bronnoysundFakeRoutes(bronnoysundSimulator)
        norgFakeRoutes(norgSimulator)
        aoOppfolgingskontorFakeRoutes()
        pdlFakeRoutes(pdlSimulator)
        nomFakeRoutes()
        navVeiledersFlateLauncherRoutes(pdlSimulator, norgSimulator)
        krrProxyFakeRoutes()
        kafkaFakeRoutes(kafkaPublisher, bronnoysundSimulator)

        // Keep previous behavior for unknown paths.
        route("{...}") {
            handle {
                respondJson(call, HttpStatusCode.NotFound, """{"error":"not found", "uri": "${this.call.request.uri}"}""")
            }
        }
    }
}
