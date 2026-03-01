package no.nav.amt.deltaker.bff.apiclients.paamelding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.deltaker.bff.apiclients.DtoMappers.utkastRequestFromUtkast
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.deltaker.model.Utkast
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.OpprettKladdRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.response.OpprettKladdResponse
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.response.UtkastResponse
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
    suspend fun opprettKladd(deltakerlisteId: UUID, personIdent: String): OpprettKladdResponse =
        performPost("pamelding", OpprettKladdRequest(deltakerlisteId, personIdent))
            .failIfNotSuccess("Kunne ikke opprette kladd i amt-deltaker.")
            .body()

    suspend fun utkast(utkast: Utkast): UtkastResponse = performPost("pamelding/${utkast.deltakerId}", utkastRequestFromUtkast(utkast))
        .failIfNotSuccess("Kunne ikke oppdatere utkast i amt-deltaker.")
        .body()

    suspend fun slettKladd(deltakerId: UUID) {
        performDelete("pamelding/$deltakerId")
            .failIfNotSuccess("Kunne ikke slette kladd i amt-deltaker.")
    }

    suspend fun avbrytUtkast(
        deltakerId: UUID,
        avbruttAv: String,
        avbruttAvEnhet: String,
    ) {
        performPost("pamelding/$deltakerId/avbryt", AvbrytUtkastRequest(avbruttAv, avbruttAvEnhet))
            .failIfNotSuccess("Kunne ikke avbryte utkast i amt-deltaker.")
    }

    suspend fun innbyggerGodkjennUtkast(deltakerId: UUID): Deltakeroppdatering =
        performPost("pamelding/$deltakerId/innbygger/godkjenn-utkast", null)
            .failIfNotSuccess("Kunne ikke fatte vedtak i amt-deltaker.")
            .body()
}
