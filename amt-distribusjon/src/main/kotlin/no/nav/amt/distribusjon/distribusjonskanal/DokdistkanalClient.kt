package no.nav.amt.distribusjon.distribusjonskanal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.distribusjon.auth.AzureAdTokenClient
import java.time.Duration
import java.util.UUID

class DokdistkanalClient(
    private val httpClient: HttpClient,
    private val azureAdTokenClient: AzureAdTokenClient,
    environment: Environment,
    private val distribusjonskanalCache: Cache<String, Distribusjonskanal> = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofMinutes(60))
        .build(),
) {
    private val scope = environment.dokdistkanalScope
    private val url = environment.dokdistkanalUrl
    private val navCallId = "amt-distribusjon"

    suspend fun bestemDistribusjonskanal(personident: String, deltakerId: UUID? = null): Distribusjonskanal {
        distribusjonskanalCache.getIfPresent(personident)?.let {
            return it
        }
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val response = httpClient.post("$url/rest/bestemDistribusjonskanal") {
            header(HttpHeaders.Authorization, token)
            header("Nav-Callid", navCallId)
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(BestemDistribusjonskanalRequest(personident)))
        }
        if (!response.status.isSuccess()) {
            if (deltakerId == null) {
                error("Kunne ikke hente distribusjonskanal, status: ${response.status} ${response.bodyAsText()}")
            } else {
                error("Kunne ikke hente distribusjonskanal for deltaker $deltakerId status: ${response.status} ${response.bodyAsText()}")
            }
        }
        val distribusjonskanal = response.body<BestemDistribusjonskanalResponse>().distribusjonskanal
        distribusjonskanalCache.put(personident, distribusjonskanal)
        return distribusjonskanal
    }
}

data class BestemDistribusjonskanalRequest(
    val brukerId: String,
    val mottakerId: String = brukerId,
    val tema: String = "OPP",
    val erArkivert: Boolean = true, // hvis denne utelates eller settes til false så defaulter responsen til PRINT
)

data class BestemDistribusjonskanalResponse(
    val distribusjonskanal: Distribusjonskanal,
)
