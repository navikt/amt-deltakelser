package no.nav.amt.deltaker.bff.clients

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.deltaker.request.AvvisForslagRequest
import no.nav.amt.internapi.deltaker.request.EndringRequest
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import org.slf4j.LoggerFactory
import java.time.Duration.ofMinutes
import java.time.ZonedDateTime
import java.util.UUID

class AmtDeltakerClient(
    baseUrl: String,
    scope: String,
    httpClient: HttpClient,
    azureAdTokenClient: AzureAdTokenClient,
    private val personIdentCache: Cache<UUID, String> = Caffeine
        .newBuilder()
        .expireAfterWrite(ofMinutes(15))
        .build(),
) : ApiClientBase(
        baseUrl = baseUrl,
        scope = scope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    ) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun getPersonidentForDeltaker(deltakerId: UUID): String = personIdentCache.getIfPresent(deltakerId)
        ?: performGet("personident/deltaker/$deltakerId")
            .failIfNotSuccess("Fant ikke personident for deltaker $deltakerId i amt-deltaker.")
            .body<PersonIdentResponse>()
            .personident
            .also { personIdentCache.put(deltakerId, it) }

    suspend fun getPersonidentForForslag(forslagId: UUID): String = personIdentCache.getIfPresent(forslagId)
        ?: performGet("personident/forslag/$forslagId")
            .failIfNotSuccess("Fant ikke personident for forslag $forslagId i amt-deltaker.")
            .body<PersonIdentResponse>()
            .personident
            .also { personIdentCache.put(forslagId, it) }

    suspend fun getDeltaker(deltakerId: UUID): DeltakerResponse = performGet("deltaker/$deltakerId")
        .failIfNotSuccess("Fant ikke deltaker $deltakerId i amt-deltaker.")
        .body()

    suspend fun getDeltakerHistorikkData(deltakerId: UUID): DeltakerHistorikkDataResponse = performGet("deltaker/$deltakerId/historikk")
        .failIfNotSuccess("Fant ikke historikkdata for $deltakerId i amt-deltaker.")
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

    suspend fun postEndreDeltaker(
        deltakerId: UUID,
        requestBody: EndringRequest,
    ) = performPost("deltaker/$deltakerId/$ENDRE_DELTAKER_URL_SEGMENT", requestBody)
        .failIfNotSuccess("Kunne ikke oppdatere deltaker $deltakerId med ${requestBody::class.java.simpleName} i amt-deltaker")
        .body<DeltakerResponse>()

    suspend fun avvisForslag(
        forslagId: UUID,
        request: AvvisForslagRequest,
    ) = performPost("avvis-forslag/$forslagId", request)
        .failIfNotSuccess("Kunne ikke avvise forslag $forslagId med ${request::class.java.simpleName} i amt-deltaker")
        .body<DeltakerResponse>()

    companion object Endepunkt {
        const val ENDRE_DELTAKER_URL_SEGMENT = "endre-deltaker"
        const val SIST_BESOKT_URL_SEGMENT = "sist-besokt"
    }
}
