import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import no.nav.poao_tilgang.poao_tilgang_test_wiremock.PoaoTilgangWiremock
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private const val POAO_TILGANG_PORT = 9001
private const val SIM_NAV_HTTP_PORT = 9002
private const val UNLEASH_PATH_PREFIX = "/unleash"
private const val MOCK_OAUTH_TOKEN_PROXY_PATH = "/mock-oauth/token"

fun main() {
    PoaoTilgangWiremock(portnummer = POAO_TILGANG_PORT)

    val simNavHttpServer = HttpServer.create(InetSocketAddress(SIM_NAV_HTTP_PORT), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path

            when {
                path.startsWith("$UNLEASH_PATH_PREFIX/") && path.endsWith("/client/features") -> respondJson(
                    exchange,
                    200,
                    unleashFeaturesJson()
                )

                path.startsWith("$UNLEASH_PATH_PREFIX/") && path.endsWith("/client/register") -> respondEmpty(exchange, 202)
                path.startsWith("$UNLEASH_PATH_PREFIX/") && path.endsWith("/client/metrics") -> respondEmpty(exchange, 202)
                path == UNLEASH_PATH_PREFIX || path == "$UNLEASH_PATH_PREFIX/api" -> respondJson(
                    exchange,
                    200,
                    "{" + "\"status\":\"ok\"" + "}"
                )

                else -> respondJson(exchange, 404, "{" + "\"error\":\"not found\"" + "}")
            }
        }
        executor = Executors.newCachedThreadPool()
        start()
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            simNavHttpServer.stop(0)
        },
    )

    println("Poao Tilgang WireMock started on port $POAO_TILGANG_PORT")
    println("Sim-nav HTTP stub started on port $SIM_NAV_HTTP_PORT")
    println("Set UNLEASH_SERVER_API_URL=http://localhost:$SIM_NAV_HTTP_PORT$UNLEASH_PATH_PREFIX/api and UNLEASH_SERVER_API_TOKEN=dummy")
    println("Mock OAuth token proxy: GET http://localhost:$SIM_NAV_HTTP_PORT$MOCK_OAUTH_TOKEN_PROXY_PATH")

    CountDownLatch(1).await()
}


private fun unleashFeaturesJson(): String {
    val features = listOf(
        "amt.enable-komet-deltakere",
        "amt.les-arena-deltakere",
        "amt.produser-deltakere-til-deltaker-ekstern-topic",
        "amt.prioriter-synkron-kommunikasjon",
        "amt.oppdater-alle-aktivitetskort",
    ).joinToString(",") { feature ->
        "{" +
                "\"name\":\"$feature\"," +
                "\"enabled\":false," +
                "\"strategies\":[]" +
                "}"
    }

    return "{" +
            "\"version\":1," +
            "\"features\":[" + features + "]" +
            "}"
}

private fun respondJson(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { output -> output.write(bytes) }
    exchange.close()
}

private fun respondEmpty(exchange: HttpExchange, status: Int) {
    exchange.sendResponseHeaders(status, -1)
    exchange.close()
}