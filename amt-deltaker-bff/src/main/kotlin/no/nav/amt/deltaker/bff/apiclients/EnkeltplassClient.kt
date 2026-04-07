package no.nav.amt.deltaker.bff.apiclients

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.paamelding.request.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.paamelding.request.OpprettKladdEnkeltplassRequest
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
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
    suspend fun opprettKladdEnkeltplass(
        tiltakskode: Tiltakskode,
        personident: String,
    ): DeltakerIdResponse = performPost(
        urlSubPath = "enkeltplass/opprett-kladd",
        requestBody = OpprettKladdEnkeltplassRequest(tiltakskode = tiltakskode, personident = personident),
    ).failIfNotSuccess("Kunne ikke opprette kladd i amt-deltaker.").body()

    suspend fun oppdaterKladdEnkeltplass(
        deltakerId: UUID,
        request: OppdaterEnkeltplassKladdRequest,
    ) = performPost(
        urlSubPath = "enkeltplass/oppdater-kladd/$deltakerId",
        requestBody = request,
    ).failIfNotSuccess("Kunne ikke oppdatere kladd i amt-deltaker.")

    suspend fun meldPaaDirekte(
        deltakerId: UUID,
        enkeltplassPamelding: EnkeltplassPameldingDecoratedRequest,
    ): HttpResponse = performPost("enkeltplass/utkast/$deltakerId/meld-paa-direkte", enkeltplassPamelding)
        .failIfNotSuccess("Kunne ikke opprette enkeltplass i amt-deltaker for deltaker $deltakerId")
}
