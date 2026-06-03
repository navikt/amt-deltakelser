package tjenester

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import http.readRequestBody
import http.respondJson
import io.ktor.http.*
import io.ktor.server.routing.*

const val ALTINN3_PATH_PREFIX = "/altinn"

private const val AUTHORIZED_PARTIES_PATH = "accessmanagement/api/v1/resourceowner/authorizedparties"
private const val RESOURCE_PREFIX = "nav_tiltaksarrangor_deltakeroversikt-"

private val resourcesByIdent =
    mapOf(
        "01019050188" to listOf("${RESOURCE_PREFIX}koordinator"),
        "14058550001" to listOf("${RESOURCE_PREFIX}veileder"),
    )

private val altinn3ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

fun Route.altinn3FakeRoutes() {
    route(ALTINN3_PATH_PREFIX) {
        post(AUTHORIZED_PARTIES_PATH) {
            val body = readRequestBody(call)
            val ident = altinn3ObjectMapper.readTree(body).path("value").asText("")

            val response =
                resourcesByIdent[ident]?.let { resources ->
                    listOf(
                        mapOf(
                            "organizationNumber" to "923456780",
                            "authorizedResources" to resources,
                            "subunits" to emptyList<Map<String, Any>>(),
                        ),
                    )
                } ?: emptyList()

            respondJson(call, HttpStatusCode.OK, altinn3ObjectMapper.writeValueAsString(response))
        }
    }
}