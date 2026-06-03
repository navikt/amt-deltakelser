import aooppfolgingskontor.aoOppfolgingskontorFakeRoutes
import aooppfolgingskontor.AoOppfolgingskontorNorgKontorOption
import brreg.BronnoysundSimulator
import brreg.bronnoysundFakeRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kafka.KafkaPublisher
import nom.nomFakeRoutes
import pdl.pdlFakeRoutes
import valp.valpFakeRoutes

fun Application.simNavModule(
    kafkaPublisher: KafkaPublisher,
    bronnoysundSimulator: BronnoysundSimulator,
    pdlSimulator: pdl.PdlSimulator,
    norgSimulator: NorgSimulator,
) {
    // install(RequestDebugPlugin)

    routing {
        simNavHomeRoutes()
        altinn3FakeRoutes()
        maskinportenFakeRoutes()
        unleashFakeRoutes()
        poaoTilgangFakeRoutes()
        veilarboppfolgingFakeRoutes(pdlSimulator)
        veilarbvedtaksstotteFakeRoutes(pdlSimulator)
        dokdistkanalFakeRoutes(pdlSimulator)
        bronnoysundFakeRoutes(bronnoysundSimulator)
        norgFakeRoutes(norgSimulator)
        aoOppfolgingskontorFakeRoutes(
            pdlDataSource = pdlSimulator,
            norgOptions = norgSimulator.allEnheter().map { enhet ->
                AoOppfolgingskontorNorgKontorOption(
                    kontorId = enhet.enhetNr,
                    kontorNavn = enhet.navn,
                    label = "${enhet.enhetNr} - ${enhet.navn}",
                )
            },
        )
        pdlFakeRoutes(pdlSimulator)
        nomFakeRoutes(pdlSimulator)
        valpFakeRoutes(bronnoysundSimulator, kafkaPublisher)
        navVeiledersFlateLauncherRoutes(pdlSimulator, norgSimulator)
        krrProxyFakeRoutes()

        // Keep previous behavior for unknown paths.
        route("{...}") {
            handle {
                respondJson(call, HttpStatusCode.NotFound, """{"error":"not found", "uri": "${this.call.request.uri}"}""")
            }
        }
    }
}
