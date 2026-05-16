package no.nav.amt.deltaker.bff.navtiltakskoordinator

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import no.nav.amt.deltaker.bff.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.AvslagRequest
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorDeltakereResponse
import no.nav.amt.internapi.tiltakskoordinator.request.DeltakereRequest
import no.nav.amt.internapi.tiltakskoordinator.request.GiAvslagRequest
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import java.util.UUID

class TiltakskoordinatorClient(
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
    companion object {
        // Forhøyet timeout for store gjennomføringer (2000+ deltakere).
        // Responsen er batchet server-side, men store lister kan fortsatt ta tid.
        private const val LARGE_LIST_REQUEST_TIMEOUT_MILLIS = 45_000L
    }

    suspend fun getDeltakereForGjennomforing(gjennomforingId: UUID): TiltakskoordinatorDeltakereResponse =
        performGet("tiltakskoordinator/deltakere/$gjennomforingId") {
            // Store gjennomføringer kan ha 2000+ deltakere — bruk forhøyet timeout.
            timeout {
                requestTimeoutMillis = LARGE_LIST_REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = LARGE_LIST_REQUEST_TIMEOUT_MILLIS
            }
        }.failIfNotSuccess("Fant ikke gjennomforing $gjennomforingId i amt-deltaker.")
            .body()

    suspend fun delMedArrangor(
        deltakerIder: List<UUID>,
        endretAv: String,
    ): List<DeltakerOppdateringResponse> = performPost(
        "tiltakskoordinator/deltakere/del-med-arrangor",
        DelMedArrangorRequest(endretAv, deltakerIder),
    ).failIfNotSuccess("Kunne ikke dele-med-arrangor i amt-deltaker. ").body()

    suspend fun tildelPlass(
        deltakerIder: List<UUID>,
        endretAv: String,
    ): List<DeltakerOppdateringResponse> = performPost(
        "tiltakskoordinator/deltakere/tildel-plass",
        DeltakereRequest(deltakerIder, endretAv),
    ).failIfNotSuccess("Kunne ikke tildele plass i amt-deltaker.").body()

    suspend fun settPaaVenteliste(
        deltakerIder: List<UUID>,
        endretAv: String,
    ): List<DeltakerOppdateringResponse> = performPost(
        "tiltakskoordinator/deltakere/sett-paa-venteliste",
        DeltakereRequest(deltakerIder, endretAv),
    ).failIfNotSuccess("Kunne ikke sette på venteliste i amt-deltaker.").body()

    suspend fun giAvslag(
        avslagRequest: AvslagRequest,
        endretAv: String,
    ): Deltakeroppdatering {
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
