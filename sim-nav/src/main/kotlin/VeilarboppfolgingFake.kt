import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.routing.*
import java.util.UUID

const val VEILARBOPPFOLGING_PATH_PREFIX = "/veilarboppfolging"

private const val VEILARBOPPFOLGING_DATA_PATH = "/veilarboppfolging/veilarboppfolging-data.json"

private val veilarboppfolgingObjectMapper = jacksonObjectMapper().findAndRegisterModules()
private val veilarboppfolgingFakeData: VeilarboppfolgingFakeData = loadVeilarboppfolgingFakeData()

fun Route.veilarboppfolgingFakeRoutes() {
    route(VEILARBOPPFOLGING_PATH_PREFIX) {

        post("api/v3/hent-veileder") {
            val fnr = readFnrFromBody(readRequestBody(call))
            val person = veilarboppfolgingFakeData.findPerson(fnr)

            respondJson(
                call,
                HttpStatusCode.OK,
                veilarboppfolgingObjectMapper.writeValueAsString(mapOf("veilederIdent" to person.veilederIdent)),
            )
        }

        post("api/v3/oppfolging/hent-perioder") {
            val fnr = readFnrFromBody(readRequestBody(call))
            val person = veilarboppfolgingFakeData.findPerson(fnr)

            respondJson(
                call,
                HttpStatusCode.OK,
                veilarboppfolgingObjectMapper.writeValueAsString(person.oppfolgingsperioder),
            )
        }

        post("api/v3/hent-manuell") {
            val fnr = readFnrFromBody(readRequestBody(call))
            val person = veilarboppfolgingFakeData.findPerson(fnr)

            respondJson(
                call,
                HttpStatusCode.OK,
                veilarboppfolgingObjectMapper.writeValueAsString(
                    mapOf("erUnderManuellOppfolging" to person.erUnderManuellOppfolging),
                ),
            )
        }

        post("api/v3/sak/{oppfolgingsperiodeId}") {
            val oppfolgingsperiodeId = call.parameters["oppfolgingsperiodeId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            if (oppfolgingsperiodeId == null) {
                respondJson(call, HttpStatusCode.BadRequest, "{\"error\":\"invalid oppfolgingsperiodeId\"}")
            } else {
                val sak = mapOf(
                    "oppfolgingsperiodeId" to oppfolgingsperiodeId,
                    "sakId" to (oppfolgingsperiodeId.mostSignificantBits and Long.MAX_VALUE),
                    "fagsaksystem" to "VEILARBOPPFOLGING",
                )
                respondJson(call, HttpStatusCode.OK, veilarboppfolgingObjectMapper.writeValueAsString(sak))
            }
        }
    }
}

private fun readFnrFromBody(body: String): String {
    val node = veilarboppfolgingObjectMapper.readTree(body)
    return node.path("fnr").asText("")
}

private fun loadVeilarboppfolgingFakeData(): VeilarboppfolgingFakeData {
    val stream = object {}.javaClass.getResourceAsStream(VEILARBOPPFOLGING_DATA_PATH)
        ?: throw IllegalStateException("Missing resource: $VEILARBOPPFOLGING_DATA_PATH")
    return stream.use { veilarboppfolgingObjectMapper.readValue(it) }
}

private data class VeilarboppfolgingFakeData(
    val persons: Map<String, VeilarboppfolgingPersonFixture>,
) {
    fun findPerson(fnr: String): VeilarboppfolgingPersonFixture = persons[fnr]
        ?: error("No Veilarboppfolging fixture found for fnr '$fnr'")
}

private data class VeilarboppfolgingPersonFixture(
    val veilederIdent: String,
    val oppfolgingsperioder: List<OppfolgingsperiodeFixture>,
    val erUnderManuellOppfolging: Boolean = false,
)

private data class OppfolgingsperiodeFixture(
    val uuid: String,
    val startDato: String,
    val sluttDato: String?,
)

