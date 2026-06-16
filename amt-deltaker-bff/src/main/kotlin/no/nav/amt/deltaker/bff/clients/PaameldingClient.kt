package no.nav.amt.deltaker.bff.clients

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.deltaker.bff.model.Utkast
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.internapi.paamelding.request.KladdRequest
import no.nav.amt.internapi.paamelding.request.OpprettKladdRequest
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import java.util.UUID

class PaameldingClient(
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
        deltakerlisteId: UUID,
        personIdent: String,
    ): DeltakerResponse = performPost(
        urlSubPath = "kladd",
        requestBody = OpprettKladdRequest(deltakerlisteId, personIdent),
    ).failIfNotSuccess("Kunne ikke opprette kladd i amt-deltaker i deltakerliste $deltakerlisteId")
        .body()

    suspend fun oppdaterKladd(
        deltakerId: UUID,
        request: KladdRequest,
    ) = performPost(
        urlSubPath = "oppdater-kladd/$deltakerId",
        requestBody = request,
    ).failIfNotSuccess("Kunne ikke oppdatere kladd i amt-deltaker for deltaker $deltakerId")

    suspend fun slettKladd(deltakerId: UUID) = performDelete("kladd/$deltakerId")
        .failIfNotSuccess("Kunne ikke slette kladd i amt-deltaker.")

    suspend fun utkast(utkast: Utkast) = performPost(
        urlSubPath = "pamelding/${utkast.deltakerId}",
        requestBody = DtoMappers.utkastRequestFromUtkast(utkast),
    ).failIfNotSuccess("Kunne ikke oppdatere utkast i amt-deltaker for deltaker ${utkast.deltakerId}")
        .body<DeltakerResponse>()

    suspend fun avbrytUtkast(
        deltakerId: UUID,
        avbruttAv: String,
        avbruttAvEnhet: String,
    ) {
        performPost(
            urlSubPath = "pamelding/$deltakerId/avbryt",
            requestBody = AvbrytUtkastRequest(avbruttAv, avbruttAvEnhet),
        ).failIfNotSuccess("Kunne ikke avbryte utkast i amt-deltaker for deltaker $deltakerId")
    }

    suspend fun innbyggerGodkjennUtkast(deltakerId: UUID) = performPost(
        urlSubPath = "pamelding/$deltakerId/innbygger/godkjenn-utkast",
        requestBody = null,
    ).failIfNotSuccess("Kunne ikke fatte vedtak i amt-deltaker for deltaker $deltakerId")
        .body<DeltakerResponse>()
}
