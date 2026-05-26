import io.ktor.http.*
import io.ktor.server.routing.*

const val VEILARBOPPFOLGING_PATH_PREFIX = "/veilarboppfolging"

private const val STATIC_VEILEDER_IDENT = "Z999999"
private const val STATIC_OPPFOLGINGSPERIODE_RESPONSE =
    """[{"uuid":"11111111-1111-1111-1111-111111111111","startDato":"2024-01-01T00:00:00Z","sluttDato":null}]"""

fun Route.veilarboppfolgingFakeRoutes() {
    route(VEILARBOPPFOLGING_PATH_PREFIX) {
        get {
            respondJson(call, HttpStatusCode.OK, """{"status":"ok"}""")
        }

        post("api/v3/hent-veileder") {
            readRequestBody(call)
            respondJson(call, HttpStatusCode.OK, """{"veilederIdent":"$STATIC_VEILEDER_IDENT"}""")
        }

        post("api/v3/oppfolging/hent-perioder") {
            readRequestBody(call)
            respondJson(call, HttpStatusCode.OK, STATIC_OPPFOLGINGSPERIODE_RESPONSE)
        }
    }
}

