package no.nav.amt.deltaker.bff.clients.arrangorsok

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess

class ArrangorsokClient(
    baseUrl: String,
    scope: String,
    httpClient: HttpClient,
    azureAdTokenClient: AzureAdTokenClient,
) : ApiClientBase(
        baseUrl = baseUrl,
        scope = scope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    ) {
    suspend fun underenhetSok(term: String): List<EnhetResponse> = performGet("api/v1/arrangor/underenhet") {
        parameter("sok", term)
    }.failIfNotSuccess("Kunne ikke hente underenheter fra Mulighetsrommet").body()
}
