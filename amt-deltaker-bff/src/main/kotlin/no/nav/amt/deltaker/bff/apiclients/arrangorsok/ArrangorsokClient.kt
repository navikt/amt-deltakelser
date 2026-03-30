package no.nav.amt.deltaker.bff.apiclients.arrangorsok

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
    suspend fun hovedenhetSok(term: String): List<EnhetResponse> = performGet("api/v1/arrangor/hovedenhet/sok/$term")
        .failIfNotSuccess("Kunne ikke hente hovedenheter fra Mulighetsrommet")
        .body()

    suspend fun hentUnderenheter(orgnummer: String): List<EnhetResponse> = performGet("api/v1/arrangor/hovedenhet/$orgnummer/underenheter")
        .failIfNotSuccess("Kunne ikke hente underenheter fra Mulighetsrommet for orgnummer $orgnummer")
        .body()
}
