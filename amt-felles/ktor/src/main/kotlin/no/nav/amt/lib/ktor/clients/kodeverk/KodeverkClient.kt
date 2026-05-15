package no.nav.amt.lib.ktor.clients.kodeverk

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.Duration

class KodeverkClient(
    baseUrl: String,
    scope: String,
    httpClient: HttpClient,
    azureAdTokenClient: AzureAdTokenClient,
    private val kodeverkCache: Cache<Tiltakskode, KodeverkResponse> = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofMinutes(15))
        .build(),
) : ApiClientBase(
        baseUrl = baseUrl,
        scope = scope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    ) {
    suspend fun hentKodeverk(tiltakskode: Tiltakskode): KodeverkResponse = kodeverkCache.getIfPresent(tiltakskode)
        ?: performGet("api/kodeverk/opplaring/kategorisering") {
            parameter("tiltakskode", tiltakskode)
        }.failIfNotSuccess("Kunne ikke hente kodeverk for tiltakskode $tiltakskode fra Mulighetsrommet")
            .body<KodeverkResponse>()
            .also { kodeverkCache.put(tiltakskode, it) }

    suspend fun sertifiseringSok(term: String): List<SertifiseringResponse> = performGet("api/kodeverk/opplaring/sertifiseringer/sok") {
        parameter("q", term)
    }.failIfNotSuccess("Kunne ikke hente sertifiseringer fra Mulighetsrommet").body()
}
