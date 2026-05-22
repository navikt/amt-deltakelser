import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private const val SIM_NAV_HTTP_PORT = 9002

fun main() {
    val kafkaPublisher = KafkaPublisher()
    val mockOAuth2Server = startMockOAuth2Server()

    val simNavHttpServer = HttpServer.create(InetSocketAddress(SIM_NAV_HTTP_PORT), 0).apply {
        createContext("/") { exchange ->
            if (!tryHandleUnleashRequest(exchange) &&
                !tryHandlePoaoTilgangRequest(exchange) &&
                !tryHandleBronnoysundRequest(exchange) &&
                !tryHandleKafkaRequest(exchange, kafkaPublisher)
            ) {
                respondJson(exchange, 404, "{\"error\":\"not found\"}")
            }
        }
        executor = Executors.newCachedThreadPool()
        start()
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            simNavHttpServer.stop(0)
            mockOAuth2Server.shutdown()
            kafkaPublisher.close()
        },
    )

    println("Sim-nav HTTP stub started on port $SIM_NAV_HTTP_PORT")
    println("Mock OAuth2 server started on http://localhost:$MOCK_OAUTH2_PORT/$MOCK_OAUTH2_ISSUER_ID")
    println("Set UNLEASH_SERVER_API_URL=http://localhost:$SIM_NAV_HTTP_PORT$UNLEASH_PATH_PREFIX/api and UNLEASH_SERVER_API_TOKEN=dummy")
    println("Set POAO_TILGANG_URL=http://localhost:$SIM_NAV_HTTP_PORT$POAO_TILGANG_PATH_PREFIX")
    println("Set app.env.brreg-url=http://localhost:$SIM_NAV_HTTP_PORT$BRONNOYSUND_PATH_PREFIX")
    println("Set AZURE_OPENID_CONFIG_ISSUER=http://localhost:$MOCK_OAUTH2_PORT/$MOCK_OAUTH2_ISSUER_ID")
    println("Set AZURE_OPENID_CONFIG_JWKS_URI=http://localhost:$MOCK_OAUTH2_PORT/$MOCK_OAUTH2_ISSUER_ID/jwks")
    println("Set AZURE_OPENID_CONFIG_TOKEN_ENDPOINT=http://localhost:$MOCK_OAUTH2_PORT/$MOCK_OAUTH2_ISSUER_ID/token")

    CountDownLatch(1).await()
}


