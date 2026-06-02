import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.routing.*

const val ALTINN3_PATH_PREFIX = "/altinn"

private const val AUTHORIZED_PARTIES_PATH = "accessmanagement/api/v1/resourceowner/authorizedparties"

private val altinn3ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

fun Route.altinn3FakeRoutes() {
    route(ALTINN3_PATH_PREFIX) {
        post(AUTHORIZED_PARTIES_PATH) {
            val body = readRequestBody(call)
            val ident = altinn3ObjectMapper.readTree(body).path("value").asText("")

            val response =
                if (ident == "12345678910") {
                    listOf(
                        mapOf(
                            "organizationNumber" to "810007842",
                            "authorizedResources" to listOf("nav_tiltaksarrangor_deltakeroversikt-koordinator"),
                            "subunits" to emptyList<Map<String, Any>>(),
                        ),
                    )
                } else {
                    emptyList<Map<String, Any>>()
                }

            respondJson(call, HttpStatusCode.OK, altinn3ObjectMapper.writeValueAsString(response))
        }
    }
}

