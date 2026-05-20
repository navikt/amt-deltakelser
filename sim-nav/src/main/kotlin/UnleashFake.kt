import com.sun.net.httpserver.HttpExchange
import java.nio.charset.StandardCharsets

const val UNLEASH_PATH_PREFIX = "/unleash"

fun tryHandleUnleashRequest(exchange: HttpExchange): Boolean {
    val path = exchange.requestURI.path

    return when {
        path.startsWith("$UNLEASH_PATH_PREFIX/") && path.endsWith("/client/features") -> {
            respondJson(exchange, 200, unleashFeaturesJson())
            true
        }

        path.startsWith("$UNLEASH_PATH_PREFIX/") && path.endsWith("/client/register") -> {
            respondEmpty(exchange, 202)
            true
        }

        path.startsWith("$UNLEASH_PATH_PREFIX/") && path.endsWith("/client/metrics") -> {
            respondEmpty(exchange, 202)
            true
        }

        path == UNLEASH_PATH_PREFIX || path == "$UNLEASH_PATH_PREFIX/api" -> {
            respondJson(exchange, 200, """{"status":"ok"}""")
            true
        }

        else -> false
    }
}

fun respondJson(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { output -> output.write(bytes) }
    exchange.close()
}

fun respondEmpty(exchange: HttpExchange, status: Int) {
    exchange.sendResponseHeaders(status, -1)
    exchange.close()
}

private fun unleashFeaturesJson(): String {
    val features = listOf(
        "amt.enable-komet-deltakere",
        "amt.les-arena-deltakere",
        "amt.produser-deltakere-til-deltaker-ekstern-topic",
        "amt.prioriter-synkron-kommunikasjon",
        "amt.oppdater-alle-aktivitetskort",
    ).joinToString(",") { feature ->
        """{"name":"$feature","enabled":true,"strategies":[]}"""
    }

    return """{"version":1,"features":[$features]}"""
}

