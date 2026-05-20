import com.sun.net.httpserver.HttpExchange
import java.nio.charset.StandardCharsets

private const val KAFKA_ENKELTPLASS_GJENNOMFORING_PATH = "/kafka/gjennomforing/enkeltplass"

fun tryHandleKafkaRequest(exchange: HttpExchange): Boolean {
    val path = exchange.requestURI.path

    return when (path) {
        KAFKA_ENKELTPLASS_GJENNOMFORING_PATH -> {
            if (exchange.requestMethod != "POST") {
                respondJson(exchange, 405, "{\"error\":\"method not allowed\"}")
                return true
            }

            // Wiring only for now: accept request body without processing.
            exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            respondJson(exchange, 202, "{\"status\":\"accepted\"}")
            true
        }

        else -> false
    }
}

