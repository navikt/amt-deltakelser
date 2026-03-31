package no.nav.amt.deltaker.bff.apiclients

import io.ktor.client.HttpClient
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess

class EnkeltplassClient(
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
    suspend fun hovedenhetSok(term: String) = performPost("api/v1/arrangor/hovedenhet/sok/$term", null)
        .failIfNotSuccess("Kunne ikke hente hovedenheter fra Mulighetsrommet")
}
