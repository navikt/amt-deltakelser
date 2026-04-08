package no.nav.amt.deltaker.bff.apiclients

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.deltaker.model.Utkast
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.enkeltplass.OpprettKladdEnkeltplassRequest
import no.nav.amt.internapi.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.internapi.paamelding.request.KladdRequest
import no.nav.amt.internapi.paamelding.request.OpprettKladdRequest
import no.nav.amt.internapi.paamelding.response.OpprettKladdResponse
import no.nav.amt.internapi.paamelding.response.UtkastResponse
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
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
    // TODO: Skal slettes
    suspend fun opprettKladdEnkeltplass(
        tiltakskode: Tiltakskode,
        personident: String,
    ): DeltakerIdResponse = performPost(
        urlSubPath = "enkeltplass/opprett-kladd",
        requestBody = OpprettKladdEnkeltplassRequest(tiltakskode = tiltakskode, personident = personident),
    ).failIfNotSuccess("Kunne ikke opprette kladd i amt-deltaker.").body()

    // TODO: Skal slettes
    suspend fun oppdaterKladdEnkeltplass(
        deltakerId: UUID,
        request: OppdaterEnkeltplassKladdRequest,
    ) = performPost(
        urlSubPath = "enkeltplass/oppdater-kladd/$deltakerId",
        requestBody = request,
    ).failIfNotSuccess("Kunne ikke oppdatere kladd i amt-deltaker.")

    suspend fun opprettKladd(
        deltakerlisteId: UUID,
        personIdent: String,
    ): OpprettKladdResponse = performPost(
        urlSubPath = "kladd",
        requestBody = OpprettKladdRequest(deltakerlisteId, personIdent),
    ).failIfNotSuccess("Kunne ikke opprette kladd i amt-deltaker.")
        .body()

    suspend fun oppdaterKladd(
        deltakerId: UUID,
        request: KladdRequest,
    ) = performPost(
        urlSubPath = "oppdater-kladd/$deltakerId",
        requestBody = request,
    ).failIfNotSuccess("Kunne ikke oppdatere kladd i amt-deltaker.")

    suspend fun slettKladd(deltakerId: UUID) {
        performDelete("kladd/$deltakerId")
            .failIfNotSuccess("Kunne ikke slette kladd i amt-deltaker.")
    }

    suspend fun utkast(utkast: Utkast): UtkastResponse = performPost(
        urlSubPath = "pamelding/${utkast.deltakerId}",
        requestBody = DtoMappers.utkastRequestFromUtkast(utkast),
    ).failIfNotSuccess("Kunne ikke oppdatere utkast i amt-deltaker.")
        .body()

    suspend fun avbrytUtkast(
        deltakerId: UUID,
        avbruttAv: String,
        avbruttAvEnhet: String,
    ) {
        performPost(
            urlSubPath = "pamelding/$deltakerId/avbryt",
            requestBody = AvbrytUtkastRequest(avbruttAv, avbruttAvEnhet),
        ).failIfNotSuccess("Kunne ikke avbryte utkast i amt-deltaker.")
    }

    suspend fun innbyggerGodkjennUtkast(deltakerId: UUID): Deltakeroppdatering = performPost(
        urlSubPath = "pamelding/$deltakerId/innbygger/godkjenn-utkast",
        requestBody = null,
    ).failIfNotSuccess("Kunne ikke fatte vedtak i amt-deltaker.")
        .body()
}
