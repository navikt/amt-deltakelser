package tjenester

import http.readRequestBody
import http.respondJson
import io.ktor.http.*
import io.ktor.server.routing.*
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*

const val MASKINPORTEN_PATH_PREFIX = "/maskinporten"

fun Route.maskinportenFakeRoutes() {
    route(MASKINPORTEN_PATH_PREFIX) {
        post("token") {
            val params = parseFormEncoded(readRequestBody(call))
            val scope = params["scope"].orEmpty()

            val accessToken = createFakeJwt(scope)
            respondJson(
                call,
                HttpStatusCode.OK,
                """
                {
                  "access_token": "$accessToken",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "expires": 3600,
                  "scope": "$scope"
                }
                """.trimIndent(),
            )
        }
    }
}

private fun createFakeJwt(scope: String): String {
    val now = Instant.now().epochSecond
    val exp = now + 3600

    val header = b64Url("""{"alg":"RS256","typ":"JWT"}""")
    val payload = b64Url(
        """
        {
          "iss":"http://localhost:9002$MASKINPORTEN_PATH_PREFIX",
          "sub":"sim-nav-maskinporten",
          "aud":"amt-altinn",
          "scope":"$scope",
          "iat":$now,
          "exp":$exp,
          "jti":"${UUID.randomUUID()}"
        }
        """.trimIndent().replace("\n", "").replace("  ", ""),
    )
    val signature = b64Url(UUID.randomUUID().toString())

    return "$header.$payload.$signature"
}

private fun b64Url(value: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

private fun parseFormEncoded(body: String): Map<String, String> =
    body
        .split("&")
        .filter { it.isNotBlank() }
        .mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator < 0) {
                null
            } else {
                val key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8)
                val value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8)
                key to value
            }
        }
        .toMap()