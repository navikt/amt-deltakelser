import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

const val BRONNOYSUND_PATH_PREFIX = "/brreg"

private const val BRONNOYSUND_DATA_PATH = "/bronnoysund-data.json"
private const val ENHETER_PATH_PREFIX = "enhetsregisteret/api/enheter/"
private const val UNDERENHETER_PATH_PREFIX = "enhetsregisteret/api/underenheter/"

class BronnoysundSimulator {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val data: BronnoysundData = loadBronnoysundData()
    private val moderenheterByOrgNr: Map<String, Map<String, Any?>> =
        data.enheter.associateBy { it["organisasjonsnummer"].toString() }
    private val underenheterByOrgNr: Map<String, Map<String, Any?>> =
        data.underenheter.associateBy { it["organisasjonsnummer"].toString() }

    fun firstOrganisasjonsnummer(): String =
        data.enheter.firstOrNull()?.get("organisasjonsnummer")?.toString()
            ?: throw IllegalStateException("No enheter found in Bronnoysund data")

    fun allEnheter(): List<Pair<String, String>> =
        data.enheter.map {
            it["organisasjonsnummer"].toString() to it["navn"].toString()
        }

    fun moderenhetOppdateringerJson(call: ApplicationCall): String {
        val filtered = filterOppdateringer(call, data.oppdateringer.enheter)
        val payload = mapOf("_embedded" to mapOf("oppdaterteEnheter" to filtered))
        return objectMapper.writeValueAsString(payload)
    }

    fun underenhetOppdateringerJson(call: ApplicationCall): String {
        val filtered = filterOppdateringer(call, data.oppdateringer.underenheter)
        val payload = mapOf("_embedded" to mapOf("oppdaterteUnderenheter" to filtered))
        return objectMapper.writeValueAsString(payload)
    }

    fun enheterGzipJson(): ByteArray =
        gzipBody(objectMapper.writeValueAsString(data.enheter))

    fun underenheterGzipJson(): ByteArray =
        gzipBody(objectMapper.writeValueAsString(data.underenheter))

    fun lookupModerenhet(organisasjonsnummer: String?): String? =
        moderenheterByOrgNr[organisasjonsnummer]?.let { objectMapper.writeValueAsString(it) }

    fun lookupUnderenhet(organisasjonsnummer: String?): String? =
        underenheterByOrgNr[organisasjonsnummer]?.let { objectMapper.writeValueAsString(it) }

    private fun filterOppdateringer(call: ApplicationCall, source: List<Oppdatering>): List<Oppdatering> {
        val fraOppdateringsId = call.request.queryParameters["oppdateringsid"]?.toIntOrNull() ?: 0
        val size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceAtLeast(0) ?: source.size
        return source
            .filter { it.oppdateringsid >= fraOppdateringsId }
            .sortedBy { it.oppdateringsid }
            .take(size)
    }

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
}

fun Route.bronnoysundFakeRoutes(simulator: BronnoysundSimulator) {
    route(BRONNOYSUND_PATH_PREFIX) {

        get("enhetsregisteret/api/oppdateringer/enheter") {
            respondJson(call, HttpStatusCode.OK, simulator.moderenhetOppdateringerJson(call))
        }

        get("enhetsregisteret/api/oppdateringer/underenheter") {
            respondJson(call, HttpStatusCode.OK, simulator.underenhetOppdateringerJson(call))
        }

        get("enhetsregisteret/api/enheter/lastned") {
            respondGzipJson(
                call,
                "application/vnd.brreg.enhetsregisteret.enhet.v1+gzip;charset=UTF-8",
                simulator.enheterGzipJson(),
            )
        }

        get("enhetsregisteret/api/underenheter/lastned") {
            respondGzipJson(
                call,
                "application/vnd.brreg.enhetsregisteret.underenhet.v1+gzip;charset=UTF-8",
                simulator.underenheterGzipJson(),
            )
        }

        get("${ENHETER_PATH_PREFIX}{organisasjonsnummer}") {
            val orgnr = call.parameters["organisasjonsnummer"]
            val json = simulator.lookupModerenhet(orgnr)
            if (json != null) {
                respondJson(call, HttpStatusCode.OK, json)
            } else {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"entity not found\"}")
            }
        }

        get("${UNDERENHETER_PATH_PREFIX}{organisasjonsnummer}") {
            val orgnr = call.parameters["organisasjonsnummer"]
            val json = simulator.lookupUnderenhet(orgnr)
            if (json != null) {
                respondJson(call, HttpStatusCode.OK, json)
            } else {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"entity not found\"}")
            }
        }
    }
}

