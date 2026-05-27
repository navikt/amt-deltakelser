import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

const val POAO_TILGANG_PATH_PREFIX = "/poao-tilgang"

private val requestIdRegex = Regex("\"requestId\"\\s*:\\s*\"([^\"]+)\"")
private val personidentRegex = Regex("\"(\\d{11})\"")

fun Route.poaoTilgangFakeRoutes() {
    route(POAO_TILGANG_PATH_PREFIX) {


        post("api/v1/policy/evaluate") {
            val body = readRequestBody(call)
            val requestIds = requestIdRegex.findAll(body).map { it.groupValues[1] }.toList()
            val results = requestIds.joinToString(",") { requestId ->
                """{"requestId":"$requestId","decision":{"type":"PERMIT","message":null,"reason":null}}"""
            }

            respondJson(call, HttpStatusCode.OK, """{"results":[$results]}""")
        }

        post("api/v1/skjermet-person") {
            val body = readRequestBody(call)
            val identer = personidentRegex.findAll(body).map { it.groupValues[1] }.toSet()
            val response = identer.joinToString(",") { personident -> "\"$personident\":false" }
            respondJson(call, HttpStatusCode.OK, "{$response}")
        }

        post("api/v1/ad-gruppe") {
            readRequestBody(call)
            respondJson(call, HttpStatusCode.OK, "[]")
        }

        post("api/v1/tilgangsattributter") {
            readRequestBody(call)
            respondJson(call, HttpStatusCode.OK, """{"kontor":"9999","skjermet":false,"diskresjonskode":"UGRADERT"}""")
        }
    }
}

