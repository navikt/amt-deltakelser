package no.nav.amt.deltaker.bff.clients

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.enkeltplass.OpprettKladdEnkeltplassRequest
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
    suspend fun opprettKladd(
        tiltakskode: Tiltakskode,
        personident: String,
    ): DeltakerIdResponse = performPost(
        urlSubPath = "enkeltplass/opprett-kladd",
        requestBody = OpprettKladdEnkeltplassRequest(
            tiltakskode = tiltakskode,
            personident = personident,
        ),
    ).failIfNotSuccess("Kunne ikke opprette kladd i amt-deltaker").body()

    suspend fun oppdaterKladd(
        deltakerId: UUID,
        kladdRequest: OppdaterEnkeltplassKladdRequest,
    ): HttpResponse = performPost(
        urlSubPath = "enkeltplass/oppdater-kladd/$deltakerId",
        requestBody = kladdRequest,
    ).failIfNotSuccess("Kunne ikke oppdatere enkeltplasskladd i amt-deltaker for deltaker $deltakerId")

    suspend fun oppdaterUtkast(
        deltakerId: UUID,
        pameldingDecoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ): DeltakerResponse = performPost(
        urlSubPath = "enkeltplass/utkast/$deltakerId",
        requestBody = pameldingDecoratedRequest,
    ).failIfNotSuccess("Kunne ikke opprette utkast i amt-deltaker for deltaker $deltakerId").body()

    suspend fun delUtkastMedInnbygger(
        deltakerId: UUID,
        pameldingDecoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ): DeltakerResponse = performPost(
        urlSubPath = "enkeltplass/utkast/$deltakerId/del-med-innbygger",
        requestBody = pameldingDecoratedRequest,
    ).failIfNotSuccess("Kunne ikke dele utkast med innbygger i amt-deltaker for deltaker $deltakerId").body()

    suspend fun meldPaaDirekte(
        deltakerId: UUID,
        pameldingDecoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ): HttpResponse = performPost(
        urlSubPath = "enkeltplass/utkast/$deltakerId/meld-paa-direkte",
        requestBody = pameldingDecoratedRequest,
    ).failIfNotSuccess("Kunne ikke opprette enkeltplass i amt-deltaker for deltaker $deltakerId")
}
