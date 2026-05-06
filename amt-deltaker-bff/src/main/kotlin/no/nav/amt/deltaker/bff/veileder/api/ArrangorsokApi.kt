package no.nav.amt.deltaker.bff.veileder.api

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel
import no.nav.amt.deltaker.bff.clients.arrangorsok.ArrangorsokClient

private const val TERMS_PARAM = "term"

private fun ApplicationCall.getTerm(): String = this.parameters[TERMS_PARAM] ?: throw IllegalArgumentException("Mangler søketerm")

fun Routing.registerArrangorsokApi(arrangorsokClient: ArrangorsokClient) {
    route("/arrangor") {
        authenticate(AuthLevel.VEILEDER.name) {
            get("/underenhet/sok/{term}") {
                val enheter = arrangorsokClient.underenhetSok(call.getTerm())
                call.respond(enheter)
            }
        }
    }
}
