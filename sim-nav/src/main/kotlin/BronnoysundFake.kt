import com.sun.net.httpserver.HttpExchange
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

const val BRONNOYSUND_PATH_PREFIX = "/brreg"

private const val BRONNOYSUND_DATA_PATH = "/bronnoysund-data.json"
private const val ENHETER_PATH_PREFIX = "/enhetsregisteret/api/enheter/"
private const val UNDERENHETER_PATH_PREFIX = "/enhetsregisteret/api/underenheter/"

private val objectMapper: ObjectMapper = jacksonObjectMapper()
private val bronnoysundData: BronnoysundData = loadBronnoysundData()
private val moderenheterByOrgNr: Map<String, Map<String, Any?>> =
    bronnoysundData.enheter.associateBy { it["organisasjonsnummer"].toString() }
private val underenheterByOrgNr: Map<String, Map<String, Any?>> =
    bronnoysundData.underenheter.associateBy { it["organisasjonsnummer"].toString() }

fun tryHandleBronnoysundRequest(exchange: HttpExchange): Boolean {
    val path = exchange.requestURI.path
    val relativePath = path.removePrefix(BRONNOYSUND_PATH_PREFIX)

    if (!path.startsWith(BRONNOYSUND_PATH_PREFIX)) {
        return false
    }

    if (exchange.requestMethod != "GET") {
        respondJson(exchange, 405, "{\"error\":\"method not allowed\"}")
        return true
    }

    return when (relativePath) {
        "/enhetsregisteret/api/oppdateringer/enheter" -> {
            respondJson(exchange, 200, moderenhetOppdateringerJson(exchange))
            true
        }

        "/enhetsregisteret/api/oppdateringer/underenheter" -> {
            respondJson(exchange, 200, underenhetOppdateringerJson(exchange))
            true
        }

        "/enhetsregisteret/api/enheter/lastned" -> {
            respondGzipJson(
                exchange,
                "application/vnd.brreg.enhetsregisteret.enhet.v1+gzip;charset=UTF-8",
                objectMapper.writeValueAsString(bronnoysundData.enheter),
            )
            true
        }

        "/enhetsregisteret/api/underenheter/lastned" -> {
            respondGzipJson(
                exchange,
                "application/vnd.brreg.enhetsregisteret.underenhet.v1+gzip;charset=UTF-8",
                objectMapper.writeValueAsString(bronnoysundData.underenheter),
            )
            true
        }

        else -> when {
            relativePath.startsWith(ENHETER_PATH_PREFIX) -> {
                handleEntityLookup(exchange, relativePath.removePrefix(ENHETER_PATH_PREFIX), moderenheterByOrgNr)
            }

            relativePath.startsWith(UNDERENHETER_PATH_PREFIX) -> {
                handleEntityLookup(exchange, relativePath.removePrefix(UNDERENHETER_PATH_PREFIX), underenheterByOrgNr)
            }

            path == BRONNOYSUND_PATH_PREFIX || path == "$BRONNOYSUND_PATH_PREFIX/" -> {
                respondJson(exchange, 200, "{\"status\":\"ok\"}")
                true
            }

            else -> false
        }
    }
}

private fun handleEntityLookup(
    exchange: HttpExchange,
    organisasjonsnummer: String,
    entitiesByOrgNr: Map<String, Map<String, Any?>>,
): Boolean {
    val entity = entitiesByOrgNr[organisasjonsnummer]
    if (entity == null) {
        respondJson(exchange, 404, "{\"error\":\"entity not found\"}")
    } else {
        respondJson(exchange, 200, objectMapper.writeValueAsString(entity))
    }
    return true
}

private fun moderenhetOppdateringerJson(exchange: HttpExchange): String {
    val filtered = filterOppdateringer(exchange, bronnoysundData.oppdateringer.enheter)
    val payload = mapOf("_embedded" to mapOf("oppdaterteEnheter" to filtered))
    return objectMapper.writeValueAsString(payload)
}

private fun underenhetOppdateringerJson(exchange: HttpExchange): String {
    val filtered = filterOppdateringer(exchange, bronnoysundData.oppdateringer.underenheter)
    val payload = mapOf("_embedded" to mapOf("oppdaterteUnderenheter" to filtered))
    return objectMapper.writeValueAsString(payload)
}

private fun filterOppdateringer(exchange: HttpExchange, source: List<Oppdatering>): List<Oppdatering> {
    val fraOppdateringsId = queryParam(exchange, "oppdateringsid")?.toIntOrNull() ?: 0
    val size = queryParam(exchange, "size")?.toIntOrNull()?.coerceAtLeast(0) ?: source.size
    return source
        .filter { it.oppdateringsid >= fraOppdateringsId }
        .sortedBy { it.oppdateringsid }
        .take(size)
}

private fun queryParam(exchange: HttpExchange, name: String): String? {
    val query = exchange.requestURI.rawQuery ?: return null
    return query
        .split("&")
        .asSequence()
        .mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) {
                null
            } else {
                val key = part.substring(0, idx)
                if (key == name) part.substring(idx + 1) else null
            }
        }
        .firstOrNull()
}

private fun respondGzipJson(exchange: HttpExchange, contentType: String, body: String) {
    val gzippedBody = ByteArrayOutputStream().use { baos ->
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(body.toByteArray(StandardCharsets.UTF_8))
        }
        baos.toByteArray()
    }

    exchange.responseHeaders.add("Content-Type", contentType)
    exchange.responseHeaders.add("Content-Encoding", "gzip")
    exchange.sendResponseHeaders(200, gzippedBody.size.toLong())
    exchange.responseBody.use { output -> output.write(gzippedBody) }
    exchange.close()
}

private fun loadBronnoysundData(): BronnoysundData {
    val stream = object {}.javaClass.getResourceAsStream(BRONNOYSUND_DATA_PATH)
        ?: throw IllegalStateException("Missing resource: $BRONNOYSUND_DATA_PATH")
    return stream.use { objectMapper.readValue(it) }
}

private data class BronnoysundData(
    val enheter: List<Map<String, Any?>>,
    val underenheter: List<Map<String, Any?>>,
    val oppdateringer: Oppdateringer,
)

private data class Oppdateringer(
    val enheter: List<Oppdatering>,
    val underenheter: List<Oppdatering>,
)

private data class Oppdatering(
    val oppdateringsid: Int,
    val dato: String,
    val organisasjonsnummer: String,
    val endringstype: String,
)

