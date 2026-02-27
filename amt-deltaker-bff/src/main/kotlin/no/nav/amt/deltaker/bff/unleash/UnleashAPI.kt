package no.nav.amt.deltaker.bff.unleash

import io.getunleash.Unleash
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

fun Routing.registerUnleashApi(unleash: Unleash) {
    fun getFeaturetoggles(features: List<String>): Map<String, Boolean> = features.associateWith { unleash.isEnabled(it) }

    authenticate("VEILEDER") {
        get("/unleash/api/feature") {
            val requestFeatures = call.parameters.getAll("feature")
            val toggles = getFeaturetoggles(requestFeatures ?: emptyList())
            call.respond(toggles)
        }
    }
}
