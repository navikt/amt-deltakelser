package no.nav.amt.deltaker.bff.apiclients.tiltakskoordinator

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.AvslagRequest
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.request.DeltakereRequest
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.request.GiAvslagRequest
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import java.util.UUID

class TiltaksKoordinatorClient(
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
    suspend fun delMedArrangor(deltakerIder: List<UUID>, endretAv: String): List<DeltakerOppdateringResponse> = performPost(
        "tiltakskoordinator/deltakere/del-med-arrangor",
        DelMedArrangorRequest(endretAv, deltakerIder),
    ).failIfNotSuccess("Kunne ikke dele-med-arrangor i amt-deltaker. ").body()

    suspend fun tildelPlass(deltakerIder: List<UUID>, endretAv: String): List<DeltakerOppdateringResponse> = performPost(
        "tiltakskoordinator/deltakere/tildel-plass",
        DeltakereRequest(deltakerIder, endretAv),
    ).failIfNotSuccess("Kunne ikke tildele plass i amt-deltaker.").body()

    suspend fun settPaaVenteliste(deltakerIder: List<UUID>, endretAv: String): List<DeltakerOppdateringResponse> = performPost(
        "tiltakskoordinator/deltakere/sett-paa-venteliste",
        DeltakereRequest(deltakerIder, endretAv),
    ).failIfNotSuccess("Kunne ikke sette på venteliste i amt-deltaker.").body()

    suspend fun giAvslag(avslagRequest: AvslagRequest, endretAv: String): Deltakeroppdatering {
        val requestBody = GiAvslagRequest(
            deltakerId = avslagRequest.deltakerId,
            avslag = EndringFraTiltakskoordinator.Avslag(
                avslagRequest.aarsak,
                avslagRequest.begrunnelse,
            ),
            endretAv = endretAv,
        )

        return performPost(
            "tiltakskoordinator/deltakere/gi-avslag",
            requestBody,
        ).failIfNotSuccess("Kunne ikke gi avslag i amt-deltaker.").body()
    }
}
