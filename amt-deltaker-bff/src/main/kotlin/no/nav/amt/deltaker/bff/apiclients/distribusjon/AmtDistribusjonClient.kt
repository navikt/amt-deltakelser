package no.nav.amt.deltaker.bff.apiclients.distribusjon

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import java.time.Duration

class AmtDistribusjonClient(
    baseUrl: String,
    scope: String,
    httpClient: HttpClient,
    azureAdTokenClient: AzureAdTokenClient,
    private val digitalBrukerCache: Cache<String, Boolean> = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofMinutes(15))
        .build(),
) : ApiClientBase(
        baseUrl = baseUrl,
        scope = scope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    ) {
    suspend fun digitalBruker(personIdent: String): Boolean {
        digitalBrukerCache.getIfPresent(personIdent)?.let {
            return it
        }

        val digitalBrukerResponse = performPost(
            urlSubPath = "digital",
            requestBody = DigitalBrukerRequest(personIdent),
        ).failIfNotSuccess("Kunne ikke hente om bruker er digital fra amt-distribusjon.")
            .body<DigitalBrukerResponse>()

        val erDigitalBruker = digitalBrukerResponse.erDigital
        digitalBrukerCache.put(personIdent, erDigitalBruker)

        return erDigitalBruker
    }
}
