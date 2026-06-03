import db.DatabaseConfig
import db.DatabaseSchema
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kafka.KafkaPublisher
import tjenester.ALTINN3_PATH_PREFIX
import tjenester.auth.MOCK_OAUTH2_ISSUER_ID
import tjenester.auth.MOCK_OAUTH2_PORT
import tjenester.auth.startMockOAuth2Server
import tjenester.intern.UNLEASH_PATH_PREFIX
import tjenester.nav.KRR_PROXY_PATH_PREFIX
import tjenester.nav.POAO_TILGANG_PATH_PREFIX
import tjenester.nav.aooppfolgingskontor.AO_OPPFOLGINGSKONTOR_PATH_PREFIX
import tjenester.nav.dokdistkanal.DOKDISTKANAL_PATH_PREFIX
import tjenester.nav.nom.NOM_PATH_PREFIX
import tjenester.nav.norg.NORG_PATH_PREFIX
import tjenester.nav.norg.NorgSimulator
import tjenester.nav.pdl.PDL_PATH_PREFIX
import tjenester.nav.pdl.PdlSimulator
import tjenester.nav.valp.VALP_PATH_PREFIX
import tjenester.nav.veilarboppfolging.VEILARBOPPFOLGING_PATH_PREFIX
import java.util.concurrent.CountDownLatch

private const val SIM_NAV_HTTP_PORT = 9002

fun main() {
    DatabaseConfig.initialize()
    DatabaseSchema.initialize()

    val bronnoysundSimulator = _root_ide_package_.tjenester.brreg.BronnoysundSimulator()
    val kafkaPublisher = KafkaPublisher(bronnoysundSimulator)
    val pdlSimulator = PdlSimulator()
    val norgSimulator = NorgSimulator()
    val mockOAuth2Server = startMockOAuth2Server()

    val simNavHttpServer = embeddedServer(
        factory = Netty,
        port = SIM_NAV_HTTP_PORT,
        module = {
            simNavModule(kafkaPublisher, bronnoysundSimulator, pdlSimulator, norgSimulator)
        },
    ).start(wait = false)

    Runtime.getRuntime().addShutdownHook(
        Thread {
            simNavHttpServer.stop(gracePeriodMillis = 0, timeoutMillis = 0)
            mockOAuth2Server.shutdown()
            kafkaPublisher.close()
            DatabaseConfig.shutdown()
        },
    )

    println("Sim-nav HTTP stub started on port $SIM_NAV_HTTP_PORT")
    println("Mock OAuth2 server started on http://localhost:${MOCK_OAUTH2_PORT}/${MOCK_OAUTH2_ISSUER_ID}")
    println("Set UNLEASH_SERVER_API_URL=http://localhost:$SIM_NAV_HTTP_PORT${UNLEASH_PATH_PREFIX}/api and UNLEASH_SERVER_API_TOKEN=dummy")
    println("Set ALTINN3_URL=http://localhost:$SIM_NAV_HTTP_PORT${ALTINN3_PATH_PREFIX}")
    println("Set POAO_TILGANG_URL=http://localhost:$SIM_NAV_HTTP_PORT${POAO_TILGANG_PATH_PREFIX}")
    println("Set app.env.brreg-url=http://localhost:$SIM_NAV_HTTP_PORT${_root_ide_package_.tjenester.brreg.BRONNOYSUND_PATH_PREFIX}")
    println("Set NORG_URL=http://localhost:$SIM_NAV_HTTP_PORT${NORG_PATH_PREFIX}")
    println("Set AO_OPPFOLGINGSKONTOR_URL=http://localhost:$SIM_NAV_HTTP_PORT${AO_OPPFOLGINGSKONTOR_PATH_PREFIX}")
    println("Set DOKDISTKANAL_URL=http://localhost:$SIM_NAV_HTTP_PORT${DOKDISTKANAL_PATH_PREFIX}")
    println("Set PDL_URL=http://localhost:$SIM_NAV_HTTP_PORT${PDL_PATH_PREFIX}")
    println("Set NOM_URL=http://localhost:$SIM_NAV_HTTP_PORT${NOM_PATH_PREFIX}")
    println("Set digdir-krr-proxy.url=http://localhost:$SIM_NAV_HTTP_PORT${KRR_PROXY_PATH_PREFIX}")
    println("Set veilarboppfolging.url=http://localhost:$SIM_NAV_HTTP_PORT")
    println("Set veilarbvedtaksstotte.url=http://localhost:$SIM_NAV_HTTP_PORT")
    println("Set AZURE_OPENID_CONFIG_ISSUER=http://localhost:${MOCK_OAUTH2_PORT}/${MOCK_OAUTH2_ISSUER_ID}")
    println("Set AZURE_OPENID_CONFIG_JWKS_URI=http://localhost:${MOCK_OAUTH2_PORT}/${MOCK_OAUTH2_ISSUER_ID}/jwks")
    println("Set AZURE_OPENID_CONFIG_TOKEN_ENDPOINT=http://localhost:${MOCK_OAUTH2_PORT}/${MOCK_OAUTH2_ISSUER_ID}/token")
    println("Local BFF proxy: http://localhost:$SIM_NAV_HTTP_PORT/amt-deltaker-bff/*")
    println("Kafka UI: http://localhost:$SIM_NAV_HTTP_PORT/kafka")
    println("nav-veileders-flate launcher: http://localhost:$SIM_NAV_HTTP_PORT/nav-veileders-flate")
    println("Valp (database view): http://localhost:$SIM_NAV_HTTP_PORT${VALP_PATH_PREFIX}")
    println("Veilarboppfolging (database view): http://localhost:$SIM_NAV_HTTP_PORT${VEILARBOPPFOLGING_PATH_PREFIX}")
    println("Nom (database view): http://localhost:$SIM_NAV_HTTP_PORT${NOM_PATH_PREFIX}")
    println("Dokdistkanal (database view): http://localhost:$SIM_NAV_HTTP_PORT${DOKDISTKANAL_PATH_PREFIX}")
    println("POST (form) http://localhost:$SIM_NAV_HTTP_PORT/kafka/tiltakstype/enkeltplass-amo")
    println("POST (form) http://localhost:$SIM_NAV_HTTP_PORT/kafka/gjennomforing/enkeltplass")

    CountDownLatch(1).await()
}
