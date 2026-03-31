package no.nav.amt.deltaker.bff.apiclients

import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import no.nav.amt.internapi.enkeltplass.MeldPaaDirekteEnkeltplassRequest
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import java.util.UUID

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
    suspend fun meldPaaDirekte(
        deltakerId: UUID,
        request: MeldPaaDirekteEnkeltplassRequest,
    ): HttpResponse = performPost("enkeltplass/utkast/$deltakerId/meld-paa-direkte", request)
        .failIfNotSuccess("Kunne ikke opprette enkeltplass i amt-deltaker for deltaker $deltakerId")
}
