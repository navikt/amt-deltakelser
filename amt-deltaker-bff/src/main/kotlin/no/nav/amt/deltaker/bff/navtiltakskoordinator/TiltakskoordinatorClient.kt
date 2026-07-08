package no.nav.amt.deltaker.bff.navtiltakskoordinator

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.AvslagRequest
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.PaginatedResult
import no.nav.amt.internapi.tiltakskoordinator.request.DeltakereRequest
import no.nav.amt.internapi.tiltakskoordinator.request.GiAvslagRequest
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerlisteFilterCountsResponse
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorDeltakerIListeResponse
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
    suspend fun getGjennomforing(gjennomforingId: UUID): GjennomforingResponse = performGet("gjennomforing/$gjennomforingId")
        .failIfNotSuccess("Fant ikke gjennomforing $gjennomforingId i amt-deltaker.")
        .body()

    suspend fun getDeltakereForGjennomforing(
        request: TiltaksKoordinatorDeltakerlisteRequest,
    ): PaginatedResult<TiltakskoordinatorDeltakerIListeResponse> = performPost(
        "tiltakskoordinator/deltakere/${request.gjennomforingId}",
        request,
    ).failIfNotSuccess("Fant ikke gjennomforing ${request.gjennomforingId} i amt-deltaker.")
        .body()

    suspend fun getDeltakereCountPerStatus(request: TiltaksKoordinatorDeltakerlisteRequest): DeltakerlisteFilterCountsResponse =
        performPost(
            "tiltakskoordinator/deltakere/status-counts",
            request,
        ).failIfNotSuccess("Kunne ikke hente deltakerantall per status i amt-deltaker.").body()

    suspend fun delMedArrangor(
        gjennomforingId: UUID,
        deltakerIder: List<UUID>,
        endretAv: String,
    ): List<DeltakerOppdateringResponse> = performPost(
        "tiltakskoordinator/deltakere/del-med-arrangor",
        DelMedArrangorRequest(endretAv, deltakerIder, gjennomforingId),
    ).failIfNotSuccess("Kunne ikke dele-med-arrangor i amt-deltaker. ").body()

    suspend fun tildelPlass(
        gjennomforingId: UUID,
        deltakerIder: List<UUID>,
        endretAv: String,
    ): List<DeltakerOppdateringResponse> = performPost(
        "tiltakskoordinator/deltakere/tildel-plass",
        DeltakereRequest(gjennomforingId, deltakerIder, endretAv),
    ).failIfNotSuccess("Kunne ikke tildele plass i amt-deltaker.").body()

    suspend fun settPaaVenteliste(
        gjennomforingId: UUID,
        deltakerIder: List<UUID>,
        endretAv: String,
    ): List<DeltakerOppdateringResponse> = performPost(
        "tiltakskoordinator/deltakere/sett-paa-venteliste",
        DeltakereRequest(gjennomforingId, deltakerIder, endretAv),
    ).failIfNotSuccess("Kunne ikke sette på venteliste i amt-deltaker.").body()

    suspend fun giAvslag(
        gjennomforingId: UUID,
        avslagRequest: AvslagRequest,
        endretAv: String,
    ): DeltakerOppdateringResponse {
        val requestBody = GiAvslagRequest(
            gjennomforingId = gjennomforingId,
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
