package no.nav.amt.deltaker.bff.veileder.api

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.amt.deltaker.bff.apiclients.arrangorsok.ArrangorsokClient
import no.nav.amt.deltaker.bff.application.plugins.AuthLevel

private val ORGNUMMER_REGEX = Regex("[8|9]\\d{8}")
private const val TERMS__PARAM = "term"
private const val ORGANISASJONSNUMMER_PARAM = "orgnummer"

private fun ApplicationCall.getTerm(): String = this.parameters[TERMS__PARAM] ?: throw IllegalArgumentException("Mangler søketerm")

private fun ApplicationCall.requireValidOrgnummer(): String {
    val orgnummer = this.parameters[ORGANISASJONSNUMMER_PARAM] ?: throw IllegalArgumentException("Mangler orgnummer")

    return if (ORGNUMMER_REGEX.matches(orgnummer)) {
        orgnummer
    } else {
        throw IllegalArgumentException("Organisasjonsnummeret må starte med 8 eller 9 og inneholde 9 siffer")
    }
}

fun Routing.registerArrangorsokApi(arrangorsokClient: ArrangorsokClient) {
    route("/arrangor") {
        authenticate(AuthLevel.VEILEDER.name) {
            get("/hovedenhet/sok/{term}") {
                val enheter = arrangorsokClient.hovedenhetSok(call.getTerm())
                call.respond(enheter)
            }
        }

        authenticate(AuthLevel.VEILEDER.name) {
            get("hovedenhet/{orgnummer}/underenheter") {
                val orgnummer = call.requireValidOrgnummer()
                val enheter = arrangorsokClient.hentUnderenheter(orgnummer)
                call.respond(enheter)
            }
        }
    }
}
