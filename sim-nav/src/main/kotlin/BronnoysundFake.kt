import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

const val BRONNOYSUND_PATH_PREFIX = "/brreg"

private const val BRONNOYSUND_DATA_PATH = "/bronnoysund-data.json"
private const val ENHETER_PATH_PREFIX = "enhetsregisteret/api/enheter/"
private const val UNDERENHETER_PATH_PREFIX = "enhetsregisteret/api/underenheter/"

private val objectMapper: ObjectMapper = jacksonObjectMapper()
private val bronnoysundData: BronnoysundData = loadBronnoysundData()
private val moderenheterByOrgNr: Map<String, Map<String, Any?>> =
    bronnoysundData.enheter.associateBy { it["organisasjonsnummer"].toString() }
private val underenheterByOrgNr: Map<String, Map<String, Any?>> =
    bronnoysundData.underenheter.associateBy { it["organisasjonsnummer"].toString() }

fun Route.bronnoysundFakeRoutes() {
    route(BRONNOYSUND_PATH_PREFIX) {
        get {
            respondJson(call, HttpStatusCode.OK, "{\"status\":\"ok\"}")
        }

        get("/") {
            respondJson(call, HttpStatusCode.OK, "{\"status\":\"ok\"}")
        }

        get("enhetsregisteret/api/oppdateringer/enheter") {
            respondJson(call, HttpStatusCode.OK, moderenhetOppdateringerJson(call))
        }

        get("enhetsregisteret/api/oppdateringer/underenheter") {
            respondJson(call, HttpStatusCode.OK, underenhetOppdateringerJson(call))
        }

        get("enhetsregisteret/api/enheter/lastned") {
            respondGzipJson(
                call,
                "application/vnd.brreg.enhetsregisteret.enhet.v1+gzip;charset=UTF-8",
                gzipBody(objectMapper.writeValueAsString(bronnoysundData.enheter)),
            )
        }

        get("enhetsregisteret/api/underenheter/lastned") {
            respondGzipJson(
                call,
                "application/vnd.brreg.enhetsregisteret.underenhet.v1+gzip;charset=UTF-8",
                gzipBody(objectMapper.writeValueAsString(bronnoysundData.underenheter)),
            )
        }

        get("${ENHETER_PATH_PREFIX}{organisasjonsnummer}") {
            handleEntityLookup(call, call.parameters["organisasjonsnummer"], moderenheterByOrgNr)
        }

        get("${UNDERENHETER_PATH_PREFIX}{organisasjonsnummer}") {
            handleEntityLookup(call, call.parameters["organisasjonsnummer"], underenheterByOrgNr)
        }
    }
}

private suspend fun handleEntityLookup(
    call: ApplicationCall,
    organisasjonsnummer: String?,
    entitiesByOrgNr: Map<String, Map<String, Any?>>,
) {
    if (organisasjonsnummer == null) {
        respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"entity not found\"}")
        return
    }

    val entity = entitiesByOrgNr[organisasjonsnummer]
    if (entity == null) {
        respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"entity not found\"}")
    } else {
        respondJson(call, HttpStatusCode.OK, objectMapper.writeValueAsString(entity))
    }
}

private fun moderenhetOppdateringerJson(call: ApplicationCall): String {
    val filtered = filterOppdateringer(call, bronnoysundData.oppdateringer.enheter)
    val payload = mapOf("_embedded" to mapOf("oppdaterteEnheter" to filtered))
    return objectMapper.writeValueAsString(payload)
}

private fun underenhetOppdateringerJson(call: ApplicationCall): String {
    val filtered = filterOppdateringer(call, bronnoysundData.oppdateringer.underenheter)
    val payload = mapOf("_embedded" to mapOf("oppdaterteUnderenheter" to filtered))
    return objectMapper.writeValueAsString(payload)
}

private fun filterOppdateringer(call: ApplicationCall, source: List<Oppdatering>): List<Oppdatering> {
    val fraOppdateringsId = queryParam(call, "oppdateringsid")?.toIntOrNull() ?: 0
    val size = queryParam(call, "size")?.toIntOrNull()?.coerceAtLeast(0) ?: source.size
    return source
        .filter { it.oppdateringsid >= fraOppdateringsId }
        .sortedBy { it.oppdateringsid }
        .take(size)
}

private fun queryParam(call: ApplicationCall, name: String): String? = call.request.queryParameters[name]

private fun gzipBody(body: String): ByteArray {
    return ByteArrayOutputStream().use { baos ->
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(body.toByteArray(StandardCharsets.UTF_8))
        }
        baos.toByteArray()
    }
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

