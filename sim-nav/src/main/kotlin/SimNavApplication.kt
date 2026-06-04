import db.AmtDeltakerRepository
import http.respondJson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kafka.KafkaPublisher
import tjenester.altinn3FakeRoutes
import tjenester.brreg.bronnoysundFakeRoutes
import tjenester.intern.unleashFakeRoutes
import tjenester.maskinportenFakeRoutes
import tjenester.nav.aooppfolgingskontor.AoOppfolgingskontorNorgKontorOption
import tjenester.nav.aooppfolgingskontor.aoOppfolgingskontorFakeRoutes
import tjenester.nav.dokdistkanal.dokdistkanalFakeRoutes
import tjenester.nav.krrProxyFakeRoutes
import tjenester.nav.nom.nomFakeRoutes
import tjenester.nav.norg.NorgSimulator
import tjenester.nav.norg.norgFakeRoutes
import tjenester.nav.pdl.PdlSimulator
import tjenester.nav.pdl.pdlFakeRoutes
import tjenester.nav.poaoTilgangFakeRoutes
import tjenester.nav.valp.valpFakeRoutes
import tjenester.nav.veilarboppfolging.veilarboppfolgingFakeRoutes
import tjenester.nav.veilarbvedtaksstotte.veilarbvedtaksstotteFakeRoutes

fun Application.simNavModule(
    kafkaPublisher: KafkaPublisher,
    bronnoysundSimulator: tjenester.brreg.BronnoysundSimulator,
    pdlSimulator: PdlSimulator,
    norgSimulator: NorgSimulator,
    amtDeltakerRepository: AmtDeltakerRepository,
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
        navVeiledersFlateLauncherRoutes(pdlSimulator, norgSimulator, amtDeltakerRepository)
        tiltaksKoordinatorFlateLauncherRoutes()
        tiltaksarrangorFlateLauncherRoutes(pdlSimulator)
        innbyggersFlateLauncherRoutes(pdlSimulator, amtDeltakerRepository)
        krrProxyFakeRoutes()

        // Keep previous behavior for unknown paths.
        route("{...}") {
            handle {
                respondJson(call, HttpStatusCode.NotFound, """{"error":"not found", "uri": "${this.call.request.uri}"}""")
            }
        }
    }
}
