import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import no.nav.poao_tilgang.poao_tilgang_test_wiremock.PoaoTilgangWiremock
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private const val POAO_TILGANG_PORT = 9001
private const val UNLEASH_PORT = 9002

fun main() {
    PoaoTilgangWiremock(portnummer = POAO_TILGANG_PORT)

    val unleashServer = HttpServer.create(InetSocketAddress(UNLEASH_PORT), 0).apply {
        createContext("/") { exchange ->
            when {
                exchange.requestURI.path.endsWith("/client/features") -> respondJson(exchange, 200, unleashFeaturesJson())
                exchange.requestURI.path.endsWith("/client/register") -> respondEmpty(exchange, 202)
                exchange.requestURI.path.endsWith("/client/metrics") -> respondEmpty(exchange, 202)
                exchange.requestURI.path == "/" || exchange.requestURI.path == "/api" -> respondJson(exchange, 200, "{" + "\"status\":\"ok\"" + "}")
                else -> respondJson(exchange, 404, "{" + "\"error\":\"not found\"" + "}")
            }
        }
        executor = Executors.newCachedThreadPool()
        start()
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            unleashServer.stop(0)
        },
    )

    println("Poao Tilgang WireMock started on port $POAO_TILGANG_PORT")
    println("Unleash stub started on port $UNLEASH_PORT")
    println("Set UNLEASH_SERVER_API_URL=http://localhost:$UNLEASH_PORT/api and UNLEASH_SERVER_API_TOKEN=dummy")

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