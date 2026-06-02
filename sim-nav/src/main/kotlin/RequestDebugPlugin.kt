import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

private val requestIdCounter = AtomicLong(0)
private val requestIdKey = AttributeKey<Long>("sim-nav-request-id")

val RequestDebugPlugin = createApplicationPlugin("RequestDebugPlugin") {
    val log = LoggerFactory.getLogger("sim-nav.request-debug")

    onCall { call ->
        val id = requestIdCounter.incrementAndGet()
        call.attributes.put(requestIdKey, id)

        log.info(
            "[{}] --> {} {} content-length={} content-type={}",
            id,
            call.request.httpMethod.value,
            call.request.uri,
            call.request.headers["content-length"] ?: "-",
            call.request.headers["content-type"] ?: "-",
        )
    }

    onCallRespond { call, body ->
        val id = call.attributes.getOrNull(requestIdKey) ?: -1L
        val status = call.response.status()?.value ?: "unset"

        log.info(
            "[{}] <-- status={} bodyType={}",
            id,
            status,
            body::class.simpleName,
        )
    }
}




