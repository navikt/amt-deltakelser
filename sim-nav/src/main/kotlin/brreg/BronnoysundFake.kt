package brreg

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.routing.*
import respondGzipJson
import respondJson
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

const val BRONNOYSUND_PATH_PREFIX = "/brreg"

private const val BRONNOYSUND_DATA_PATH = "/bronnoysund-data.json"
private const val ENHETER_PATH_PREFIX = "enhetsregisteret/api/enheter/"
private const val UNDERENHETER_PATH_PREFIX = "enhetsregisteret/api/underenheter/"

private val objectMapper = jacksonObjectMapper()

private fun toJson(value: Any): String = objectMapper.writeValueAsString(value)

private fun gzipJson(value: Any): ByteArray {
    val json = toJson(value).toByteArray(StandardCharsets.UTF_8)
    return ByteArrayOutputStream().use { baos ->
        GZIPOutputStream(baos).use { it.write(json) }
        baos.toByteArray()
    }
}

fun Route.bronnoysundFakeRoutes(simulator: BronnoysundSimulator) {
    route(BRONNOYSUND_PATH_PREFIX) {

        get("enhetsregisteret/api/oppdateringer/enheter") {
            respondJson(call, HttpStatusCode.OK, toJson(simulator.moderenhetOppdateringer(call)))
        }

        get("enhetsregisteret/api/oppdateringer/underenheter") {
            respondJson(call, HttpStatusCode.OK, toJson(simulator.underenhetOppdateringer(call)))
        }

        get("enhetsregisteret/api/enheter/lastned") {
            respondGzipJson(
                call,
                "application/vnd.brreg.enhetsregisteret.enhet.v1+gzip;charset=UTF-8",
                gzipJson(simulator.enheter()),
            )
        }

        get("enhetsregisteret/api/underenheter/lastned") {
            respondGzipJson(
                call,
                "application/vnd.brreg.enhetsregisteret.underenhet.v1+gzip;charset=UTF-8",
                gzipJson(simulator.underenheter()),
            )
        }

        get("${ENHETER_PATH_PREFIX}{organisasjonsnummer}") {
            val entity = simulator.lookupModerenhet(call.parameters["organisasjonsnummer"])
            if (entity != null) {
                respondJson(call, HttpStatusCode.OK, toJson(entity))
            } else {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"entity not found\"}")
            }
        }

        get("${UNDERENHETER_PATH_PREFIX}{organisasjonsnummer}") {
            val entity = simulator.lookupUnderenhet(call.parameters["organisasjonsnummer"])
            if (entity != null) {
                respondJson(call, HttpStatusCode.OK, toJson(entity))
            } else {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"entity not found\"}")
            }
        }
    }
}
