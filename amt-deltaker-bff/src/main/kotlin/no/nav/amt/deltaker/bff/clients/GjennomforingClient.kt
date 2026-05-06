package no.nav.amt.deltaker.bff.clients

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import java.util.UUID

class GjennomforingClient(
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
    suspend fun getGjennomforing(gjennomforingId: UUID): GjennomforingResponse = performGet("gjennomforing/$gjennomforingId")
        .failIfNotSuccess("Fant ikke gjennomforing $gjennomforingId i amt-deltaker.")
        .body()
}
