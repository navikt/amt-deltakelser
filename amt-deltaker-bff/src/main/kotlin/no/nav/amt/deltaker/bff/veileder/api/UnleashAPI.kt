package no.nav.amt.deltaker.bff.veileder.api

import io.getunleash.Unleash
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel

fun Routing.registerUnleashApi(unleash: Unleash) {
    fun getFeaturetoggles(features: List<String>): Map<String, Boolean> = features.associateWith { unleash.isEnabled(it) }

    authenticate(AuthLevel.VEILEDER.name) {
        get("/unleash/api/feature") {
            val requestFeatures = call.parameters.getAll("feature")
            val toggles = getFeaturetoggles(requestFeatures ?: emptyList())
            call.respond(toggles)
        }
    }
}
