import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.routing.*

const val NORG_PATH_PREFIX = "/norg"

private val norgObjectMapper = jacksonObjectMapper()

fun Route.norgFakeRoutes(dataSource: NorgDataSource) {
    route(NORG_PATH_PREFIX) {

        get("norg2/api/v1/enhet/{enhetId}") {
            val enhetId = call.parameters["enhetId"]
            val enhet = enhetId?.let { dataSource.findEnhet(it) }

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

            val enheter = dataSource.findEnheter(enhetsnummerListe)
            respondJson(call, HttpStatusCode.OK, norgObjectMapper.writeValueAsString(enheter))
        }
    }
}


