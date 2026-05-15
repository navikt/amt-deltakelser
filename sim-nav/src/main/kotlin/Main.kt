import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import no.nav.poao_tilgang.poao_tilgang_test_wiremock.PoaoTilgangWiremock
import java.net.URI
import java.net.URLEncoder
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private const val POAO_TILGANG_PORT = 9001
private const val SIM_NAV_HTTP_PORT = 9002
private const val UNLEASH_PATH_PREFIX = "/unleash"
private const val MOCK_OAUTH_TOKEN_PROXY_PATH = "/mock-oauth/token"
private const val MOCK_OAUTH_TOKEN_URL = "http://localhost:9000/azure/token"
private const val MOCK_OAUTH_CLIENT_ID = "local-client-id"
private const val MOCK_OAUTH_CLIENT_SECRET = "local-secret"
private const val MOCK_OAUTH_AUDIENCE = "local-client-id"

fun main() {
    PoaoTilgangWiremock(portnummer = POAO_TILGANG_PORT)

    val httpClient = HttpClient.newBuilder().build()

    val simNavHttpServer = HttpServer.create(InetSocketAddress(SIM_NAV_HTTP_PORT), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path

            when {
                path == MOCK_OAUTH_TOKEN_PROXY_PATH -> handleMockOauthToken(exchange, httpClient)
                path.startsWith("$UNLEASH_PATH_PREFIX/") && path.endsWith("/client/features") -> respondJson(exchange, 200, unleashFeaturesJson())
                path.startsWith("$UNLEASH_PATH_PREFIX/") && path.endsWith("/client/register") -> respondEmpty(exchange, 202)
                path.startsWith("$UNLEASH_PATH_PREFIX/") && path.endsWith("/client/metrics") -> respondEmpty(exchange, 202)
                path == UNLEASH_PATH_PREFIX || path == "$UNLEASH_PATH_PREFIX/api" -> respondJson(exchange, 200, "{" + "\"status\":\"ok\"" + "}")
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

private fun handleMockOauthToken(exchange: HttpExchange, httpClient: HttpClient) {
    val queryParams = parseQueryParams(exchange.requestURI.rawQuery)
    val tokenUrl = System.getenv("MOCK_OAUTH_TOKEN_URL") ?: MOCK_OAUTH_TOKEN_URL

    val formParams = linkedMapOf(
        "grant_type" to "client_credentials",
        "client_id" to (queryParams["client_id"] ?: System.getenv("MOCK_OAUTH_CLIENT_ID") ?: MOCK_OAUTH_CLIENT_ID),
        "client_secret" to (queryParams["client_secret"] ?: System.getenv("MOCK_OAUTH_CLIENT_SECRET") ?: MOCK_OAUTH_CLIENT_SECRET),
        "aud" to (queryParams["aud"] ?: System.getenv("MOCK_OAUTH_AUDIENCE") ?: MOCK_OAUTH_AUDIENCE),
    )

    val formBody = formParams.entries.joinToString("&") { (key, value) ->
        "${urlEncode(key)}=${urlEncode(value)}"
    }

    try {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        respondJson(exchange, response.statusCode(), response.body())
    } catch (e: Exception) {
        respondJson(
            exchange,
            502,
            "{" +
                "\"error\":\"Failed to fetch token from mock oauth\"," +
                "\"message\":\"${escapeJson(e.message ?: "Unknown error") }\"" +
                "}",
        )
    }
}

private fun parseQueryParams(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()

    return rawQuery
        .split("&")
        .mapNotNull { pair ->
            val keyValue = pair.split("=", limit = 2)
            val key = keyValue.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = keyValue.getOrElse(1) { "" }
            key to value
        }
        .toMap()
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun escapeJson(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")

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