package no.nav.amt.lib.ktor.clients.arrangor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.deltaker.bff.apiclients.arrangor.ArrangorResponse
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import java.util.UUID

class AmtArrangorClient(
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
    suspend fun hentArrangor(orgnummer: String): ArrangorResponse = performGet("api/service/arrangor/organisasjonsnummer/$orgnummer")
        .failIfNotSuccess("Kunne ikke hente arrangør med orgnummer $orgnummer fra amt-arrangør.")
        .body()

    suspend fun hentArrangor(id: UUID): ArrangorResponse = performGet("api/service/arrangor/$id")
        .failIfNotSuccess("Kunne ikke hente arrangør med id $id fra amt-arrangør.")
        .body()
}
