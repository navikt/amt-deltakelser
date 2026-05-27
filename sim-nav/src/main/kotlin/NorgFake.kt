import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

const val NORG_PATH_PREFIX = "/norg"

private const val NORG_DATA_PATH = "/norg/norg-data.json"

private val norgObjectMapper = jacksonObjectMapper()
private val norgFakeData: NorgFakeData = loadNorgFakeData()

fun Route.norgFakeRoutes() {
    route(NORG_PATH_PREFIX) {

        get("norg2/api/v1/enhet/{enhetId}") {
            val enhetId = call.parameters["enhetId"]
            val enhet = enhetId?.let { norgFakeData.enheter[it] }

            if (enhet == null) {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"entity not found\"}")
            } else {
                respondJson(call, HttpStatusCode.OK, norgObjectMapper.writeValueAsString(enhet))
            }
        }

        get("norg2/api/v1/enhet") {
            val enhetsnummerListe = call.request.queryParameters["enhetsnummerListe"]
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val enheter = enhetsnummerListe.mapNotNull { norgFakeData.enheter[it] }
            respondJson(call, HttpStatusCode.OK, norgObjectMapper.writeValueAsString(enheter))
        }
    }
}


private fun loadNorgFakeData(): NorgFakeData {
    val stream = object {}.javaClass.getResourceAsStream(NORG_DATA_PATH)
        ?: throw IllegalStateException("Missing resource: $NORG_DATA_PATH")
    return stream.use { norgObjectMapper.readValue(it) }
}

private data class NorgFakeData(
    val enheter: Map<String, NorgNavEnhetFixture>,
)

private data class NorgNavEnhetFixture(
    val navn: String,
    val enhetNr: String,
)

