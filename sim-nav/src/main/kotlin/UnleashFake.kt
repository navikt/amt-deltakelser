import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

const val UNLEASH_PATH_PREFIX = "/unleash"

fun Route.unleashFakeRoutes() {
    route(UNLEASH_PATH_PREFIX) {
        get {
            respondJson(call, HttpStatusCode.OK, """{"status":"ok"}""")
        }

        get("api") {
            respondJson(call, HttpStatusCode.OK, """{"status":"ok"}""")
        }

        get("api/client/features") {
            respondJson(call, HttpStatusCode.OK, unleashFeaturesJson())
        }

        post("api/client/register") {
            respondEmpty(call, HttpStatusCode.Accepted)
        }

        post("api/client/metrics") {
            respondEmpty(call, HttpStatusCode.Accepted)
        }
    }
}

private fun unleashFeaturesJson(): String {
    val features = listOf(
        "amt.enable-komet-deltakere",
        "amt.les-arena-deltakere",
        "amt.produser-deltakere-til-deltaker-ekstern-topic",
        "amt.prioriter-synkron-kommunikasjon",
        "amt.oppdater-alle-aktivitetskort",
    ).joinToString(",") { feature ->
        """{"name":"$feature","enabled":true,"strategies":[]}"""
    }

    return """{"version":1,"features":[$features]}"""
}

