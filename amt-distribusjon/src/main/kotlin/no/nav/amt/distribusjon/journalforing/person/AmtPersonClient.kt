package no.nav.amt.distribusjon.journalforing.person

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.distribusjon.auth.AzureAdTokenClient
import no.nav.amt.distribusjon.journalforing.person.model.NavBruker

class AmtPersonClient(
    private val httpClient: HttpClient,
    private val azureAdTokenClient: AzureAdTokenClient,
    environment: Environment,
) {
    private val scope = environment.amtPersonScope
    private val url = environment.amtPersonUrl

    suspend fun hentNavBruker(personident: String): NavBruker {
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val response = httpClient.post("$url/api/nav-bruker") {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(NavBrukerRequest(personident)))
        }
        if (!response.status.isSuccess()) {
            error("Kunne ikke hente nav-bruker fra amt-person-service")
        }
        return response.body()
    }
}

data class NavBrukerRequest(
    val personident: String,
)
