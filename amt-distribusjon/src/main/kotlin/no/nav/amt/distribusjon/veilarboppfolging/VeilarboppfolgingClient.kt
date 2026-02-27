package no.nav.amt.distribusjon.veilarboppfolging

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.distribusjon.auth.AzureAdTokenClient
import java.time.Duration
import java.util.UUID

class VeilarboppfolgingClient(
    private val httpClient: HttpClient,
    private val azureAdTokenClient: AzureAdTokenClient,
    environment: Environment,
    private val manuellOppfolgingCache: Cache<String, Boolean> = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofMinutes(60))
        .build(),
) {
    private val scope = environment.veilarboppfolgingScope
    private val url = environment.veilarboppfolgingUrl
    private val consumerId: String = "amt-distribusjon"

    suspend fun opprettEllerHentSak(oppfolgingsperiodeId: UUID): Sak {
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val response = httpClient.post("$url/veilarboppfolging/api/v3/sak/$oppfolgingsperiodeId") {
            header(HttpHeaders.Authorization, token)
            header("Nav-Consumer-Id", consumerId)
            contentType(ContentType.Application.Json)
        }

        if (!response.status.isSuccess()) {
            error("Kunne ikke hente sak fra veilarboppfolging for oppfolgingsperiode $oppfolgingsperiodeId, status: ${response.status}")
        }
        return response.body()
    }

    suspend fun erUnderManuellOppfolging(personident: String): Boolean {
        manuellOppfolgingCache.getIfPresent(personident)?.let {
            return it
        }
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val response = httpClient.post("$url/veilarboppfolging/api/v3/hent-manuell") {
            header(HttpHeaders.Authorization, token)
            header("Nav-Consumer-Id", consumerId)
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(ManuellStatusRequest(personident)))
        }
        if (!response.status.isSuccess()) {
            error("Kunne ikke hente manuell oppfølging fra veilarboppfolging, status: ${response.status}")
        }
        val erUnderManuellOppfolging = response.body<ManuellV2Response>().erUnderManuellOppfolging
        manuellOppfolgingCache.put(personident, erUnderManuellOppfolging)
        return erUnderManuellOppfolging
    }
}

data class Sak(
    val oppfolgingsperiodeId: UUID,
    val sakId: Long,
    val fagsaksystem: String,
)

data class ManuellStatusRequest(
    val fnr: String,
)

data class ManuellV2Response(
    val erUnderManuellOppfolging: Boolean,
)
