import aooppfolgingskontor.AO_OPPFOLGINGSKONTOR_PATH_PREFIX
import brreg.BRONNOYSUND_PATH_PREFIX
import brreg.BronnoysundSimulator
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kafka.KafkaPublisher
import nom.NOM_PATH_PREFIX
import pdl.PDL_PATH_PREFIX
import pdl.PdlSimulator
import valp.VALP_PATH_PREFIX
import java.util.concurrent.CountDownLatch

private const val SIM_NAV_HTTP_PORT = 9002

fun main() {
    DatabaseConfig.initialize()
    DatabaseSchema.initialize()

    val bronnoysundSimulator = BronnoysundSimulator()
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
    println("Mock OAuth2 server started on http://localhost:$MOCK_OAUTH2_PORT/$MOCK_OAUTH2_ISSUER_ID")
    println("Set UNLEASH_SERVER_API_URL=http://localhost:$SIM_NAV_HTTP_PORT$UNLEASH_PATH_PREFIX/api and UNLEASH_SERVER_API_TOKEN=dummy")
    println("Set ALTINN3_URL=http://localhost:$SIM_NAV_HTTP_PORT$ALTINN3_PATH_PREFIX")
    println("Set POAO_TILGANG_URL=http://localhost:$SIM_NAV_HTTP_PORT$POAO_TILGANG_PATH_PREFIX")
    println("Set app.env.brreg-url=http://localhost:$SIM_NAV_HTTP_PORT${BRONNOYSUND_PATH_PREFIX}")
    println("Set NORG_URL=http://localhost:$SIM_NAV_HTTP_PORT$NORG_PATH_PREFIX")
    println("Set AO_OPPFOLGINGSKONTOR_URL=http://localhost:$SIM_NAV_HTTP_PORT$AO_OPPFOLGINGSKONTOR_PATH_PREFIX")
    println("Set PDL_URL=http://localhost:$SIM_NAV_HTTP_PORT${PDL_PATH_PREFIX}")
    println("Set NOM_URL=http://localhost:$SIM_NAV_HTTP_PORT${NOM_PATH_PREFIX}")
    println("Set digdir-krr-proxy.url=http://localhost:$SIM_NAV_HTTP_PORT$KRR_PROXY_PATH_PREFIX")
    println("Set veilarboppfolging.url=http://localhost:$SIM_NAV_HTTP_PORT")
    println("Set veilarbvedtaksstotte.url=http://localhost:$SIM_NAV_HTTP_PORT")
    println("Set AZURE_OPENID_CONFIG_ISSUER=http://localhost:$MOCK_OAUTH2_PORT/$MOCK_OAUTH2_ISSUER_ID")
    println("Set AZURE_OPENID_CONFIG_JWKS_URI=http://localhost:$MOCK_OAUTH2_PORT/$MOCK_OAUTH2_ISSUER_ID/jwks")
    println("Set AZURE_OPENID_CONFIG_TOKEN_ENDPOINT=http://localhost:$MOCK_OAUTH2_PORT/$MOCK_OAUTH2_ISSUER_ID/token")
    println("Kafka UI: http://localhost:$SIM_NAV_HTTP_PORT/kafka")
    println("nav-veileders-flate launcher: http://localhost:$SIM_NAV_HTTP_PORT/nav-veileders-flate")
    println("Valp (database view): http://localhost:$SIM_NAV_HTTP_PORT$VALP_PATH_PREFIX")
    println("POST (form) http://localhost:$SIM_NAV_HTTP_PORT/kafka/tiltakstype/enkeltplass-amo")
    println("POST (form) http://localhost:$SIM_NAV_HTTP_PORT/kafka/gjennomforing/enkeltplass")

    CountDownLatch(1).await()
}
