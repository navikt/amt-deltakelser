import com.sun.net.httpserver.HttpExchange
import java.nio.charset.StandardCharsets

const val POAO_TILGANG_PATH_PREFIX = "/poao-tilgang"

private val requestIdRegex = Regex("\"requestId\"\\s*:\\s*\"([^\"]+)\"")
private val personidentRegex = Regex("\"(\\d{11})\"")

fun tryHandlePoaoTilgangRequest(exchange: HttpExchange): Boolean {
    val path = exchange.requestURI.path

    return when (path.removePrefix(POAO_TILGANG_PATH_PREFIX)) {
        "/api/v1/policy/evaluate" -> {
            val body = readRequestBody(exchange)
            val requestIds = requestIdRegex.findAll(body).map { it.groupValues[1] }.toList()
            val results = requestIds.joinToString(",") { requestId ->
                """{"requestId":"$requestId","decision":{"type":"PERMIT","message":null,"reason":null}}"""
            }

            respondJson(exchange, 200, """{"results":[$results]}""")
            true
        }

        "/api/v1/skjermet-person" -> {
            val body = readRequestBody(exchange)
            val identer = personidentRegex.findAll(body).map { it.groupValues[1] }.toSet()
            val response = identer.joinToString(",") { personident -> "\"$personident\":false" }
            respondJson(exchange, 200, "{$response}")
            true
        }

        "/api/v1/ad-gruppe" -> {
            readRequestBody(exchange)
            respondJson(exchange, 200, "[]")
            true
        }

        "/api/v1/tilgangsattributter" -> {
            readRequestBody(exchange)
            respondJson(exchange, 200, """{"kontor":"9999","skjermet":false,"diskresjonskode":"UGRADERT"}""")
            true
        }

        "/", "", "/api" -> {
            respondJson(exchange, 200, """{"status":"ok"}""")
            true
        }

        else -> false
    }
}

private fun readRequestBody(exchange: HttpExchange): String {
    return exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}

