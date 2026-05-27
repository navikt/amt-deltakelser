import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.routing.*
import shared.loadJsonResource

const val VEILARBVEDTAKSSTOTTE_PATH_PREFIX = "/veilarbvedtaksstotte"

private const val VEILARBVEDTAKSSTOTTE_DATA_PATH = "/veilarbvedtaksstotte/veilarbvedtaksstotte-data.json"

private val veilarbvedtaksstotteObjectMapper = jacksonObjectMapper()
private val veilarbvedtaksstotteFakeData: VeilarbvedtaksstotteFakeData = loadVeilarbvedtaksstotteFakeData()

fun Route.veilarbvedtaksstotteFakeRoutes() {
    route(VEILARBVEDTAKSSTOTTE_PATH_PREFIX) {

        post("api/hent-gjeldende-14a-vedtak") {
            val fnr = readFnrFromBody(readRequestBody(call))
            val person = veilarbvedtaksstotteFakeData.findPerson(fnr)

            if (person.innsatsgruppe == null) {
                respondEmpty(call, HttpStatusCode.NoContent)
            } else {
                respondJson(
                    call,
                    HttpStatusCode.OK,
                    veilarbvedtaksstotteObjectMapper.writeValueAsString(
                        Gjeldende14aVedtakResponse(
                            innsatsgruppe = person.innsatsgruppe,
                        ),
                    ),
                )
            }
        }
    }
}

private fun readFnrFromBody(body: String): String {
    val node = veilarbvedtaksstotteObjectMapper.readTree(body)
    return node.path("fnr").asText("")
}

private fun loadVeilarbvedtaksstotteFakeData(): VeilarbvedtaksstotteFakeData {
    return loadJsonResource(veilarbvedtaksstotteObjectMapper, VEILARBVEDTAKSSTOTTE_DATA_PATH)
}

private data class VeilarbvedtaksstotteFakeData(
    val defaultFnr: String,
    val persons: Map<String, VeilarbvedtaksstottePersonFixture>,
) {
    fun findPerson(fnr: String): VeilarbvedtaksstottePersonFixture = persons[fnr]
        ?: persons[defaultFnr]
        ?: error("No Veilarbvedtaksstotte fixture found for fnr '$fnr' and missing default fixture '$defaultFnr'")
}

private data class VeilarbvedtaksstottePersonFixture(
    val innsatsgruppe: String?,
)

private data class Gjeldende14aVedtakResponse(
    val innsatsgruppe: String,
)

