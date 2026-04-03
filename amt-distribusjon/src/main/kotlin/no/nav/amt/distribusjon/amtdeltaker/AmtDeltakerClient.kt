package no.nav.amt.distribusjon.amtdeltaker

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import java.util.UUID

class AmtDeltakerClient(
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
    suspend fun getDeltaker(deltakerId: UUID): DeltakerResponse = performGet("deltaker/$deltakerId")
        .failIfNotSuccess("Fant ikke deltaker $deltakerId i amt-deltaker.")
        .body()
}
