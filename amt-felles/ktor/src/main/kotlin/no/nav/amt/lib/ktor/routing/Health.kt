package no.nav.amt.lib.ktor.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.util.AttributeKey

val isReadyKey = AttributeKey<Boolean>("isReady")

fun Routing.registerHealthApi() {
    get("/internal/health/liveness") {
        call.respondText("I'm alive!")
    }

    get("/internal/health/readiness") {
        val isReady = call.application.attributes.getOrNull(isReadyKey) ?: false

        if (isReady) {
            call.respondText("I'm ready!")
        } else {
            call.respondText("I'm not ready!", status = HttpStatusCode.ServiceUnavailable)
        }
    }
}
