package no.nav.amt.deltaker.bff.apiclients.deltaker

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerEndringResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerResponse
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
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
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun getDeltaker(deltakerId: UUID): DeltakerResponse = performGet("deltaker/$deltakerId")
        .failIfNotSuccess("Fant ikke deltaker $deltakerId i amt-deltaker.")
        .body()

    suspend fun getDeltakerHistorikk(deltakerId: UUID): List<DeltakerHistorikk> = performGet("deltaker/$deltakerId/historikk")
        .failIfNotSuccess("Fant ikke deltakerhistorikk for $deltakerId i amt-deltaker.")
        .body()

    suspend fun sistBesokt(
        deltakerId: UUID,
        sistBesokt: ZonedDateTime,
    ) {
        val response = performPost("deltaker/$deltakerId/$SIST_BESOKT_URL_SEGMENT", sistBesokt)

        if (!response.status.isSuccess()) {
            log.warn(
                "Kunne ikke endre $SIST_BESOKT_URL_SEGMENT i amt-deltaker. Status=${response.status.value} error=${response.bodyAsText()}",
            )
        }
    }

    suspend fun historikk(deltakerId: UUID) {
        val response = performGet("deltaker/$deltakerId/historikk")

        if (!response.status.isSuccess()) {
            log.warn(
                "Kunne ikke hente deltakerhistorikk for $deltakerId i amt-deltaker. Status=${response.status.value} error=${response.bodyAsText()}",
            )
        }
    }

    suspend fun postEndreDeltaker(
        deltakerId: UUID,
        requestBody: EndringRequest,
    ): DeltakerEndringResponse = performPost("deltaker/$deltakerId/$ENDRE_DELTAKER_URL_SEGMENT", requestBody)
        .failIfNotSuccess("Kunne ikke oppdatere deltaker $deltakerId med ${requestBody::class.java.simpleName} i amt-deltaker")
        .body()

    companion object Endepunkt {
        const val ENDRE_DELTAKER_URL_SEGMENT = "endre-deltaker"
        const val SIST_BESOKT_URL_SEGMENT = "sist-besokt"
    }
}
