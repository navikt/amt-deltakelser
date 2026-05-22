import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private const val KAFKA_ENKELTPLASS_GJENNOMFORING_PATH = "/kafka/gjennomforing/enkeltplass"
private const val KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH = "/kafka/tiltakstype/enkeltplass-amo"

fun Route.kafkaFakeRoutes(
    kafkaPublisher: KafkaPublisher,
) {
    route(KAFKA_ENKELTPLASS_GJENNOMFORING_PATH) {
        post {
            try {
                kafkaPublisher.publishGjennomforingEnkeltplass()
                respondJson(call, HttpStatusCode.Accepted, "{\"status\":\"accepted\"}")
            } catch (_: Exception) {
                respondJson(call, HttpStatusCode.InternalServerError, "{\"error\":\"failed to publish kafka message\"}")
            }
        }
    }

    route(KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH) {
        post {
            try {
                kafkaPublisher.publishTiltakstypeEnkeltplassArbeidsmarkedsopplaering()
                respondJson(call, HttpStatusCode.Accepted, "{\"status\":\"accepted\"}")
            } catch (_: Exception) {
                respondJson(call, HttpStatusCode.InternalServerError, "{\"error\":\"failed to publish kafka message\"}")
            }
        }
    }
}

