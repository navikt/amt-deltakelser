import com.sun.net.httpserver.HttpExchange

private const val KAFKA_ENKELTPLASS_GJENNOMFORING_PATH = "/kafka/gjennomforing/enkeltplass"
private const val KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH = "/kafka/tiltakstype/enkeltplass-amo"

fun tryHandleKafkaRequest(
    exchange: HttpExchange,
    kafkaPublisher: KafkaPublisher,
): Boolean {
    val path = exchange.requestURI.path

    return when (path) {
        KAFKA_ENKELTPLASS_GJENNOMFORING_PATH -> {
            if (exchange.requestMethod != "POST") {
                respondJson(exchange, 405, "{\"error\":\"method not allowed\"}")
                return true
            }

            try {
                kafkaPublisher.publishGjennomforingEnkeltplass()
                respondJson(exchange, 202, "{\"status\":\"accepted\"}")
            } catch (_: Exception) {
                respondJson(exchange, 500, "{\"error\":\"failed to publish kafka message\"}")
            }
            true
        }

        KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH -> {
            if (exchange.requestMethod != "POST") {
                respondJson(exchange, 405, "{\"error\":\"method not allowed\"}")
                return true
            }

            try {
                kafkaPublisher.publishTiltakstypeEnkeltplassArbeidsmarkedsopplaering()
                respondJson(exchange, 202, "{\"status\":\"accepted\"}")
            } catch (_: Exception) {
                respondJson(exchange, 500, "{\"error\":\"failed to publish kafka message\"}")
            }
            true
        }

        else -> false
    }
}

