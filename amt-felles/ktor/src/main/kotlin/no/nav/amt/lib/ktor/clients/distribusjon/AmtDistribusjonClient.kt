package no.nav.amt.lib.ktor.clients.distribusjon

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
        .expireAfterWrite(Duration.ofMinutes(CACHE_EXPIRATION_MINUTES))
        .build(),
) : ApiClientBase(
        baseUrl = baseUrl,
        scope = scope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    ) {
    suspend fun digitalBruker(personIdent: String): Boolean = digitalBrukerCache.getIfPresent(personIdent)
        ?: performPost(
            urlSubPath = "digital",
            requestBody = DigitalBrukerRequest(personIdent),
        ).failIfNotSuccess("Kunne ikke hente om bruker er digital fra amt-distribusjon.")
            .body<DigitalBrukerResponse>()
            .erDigital
            .also { digitalBrukerCache.put(personIdent, it) }

    companion object {
        private const val CACHE_EXPIRATION_MINUTES = 120L
    }
}
