package no.nav.amt.lib.ktor.clients.kodeverk

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.Duration

class OpplaringKategoriseringClient(
    baseUrl: String,
    scope: String,
    httpClient: HttpClient,
    azureAdTokenClient: AzureAdTokenClient,
    private val kategoriseringCache: Cache<Tiltakskode, OpplaringKategoriseringResponse> = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofMinutes(15))
        .build(),
) : ApiClientBase(
        baseUrl = baseUrl,
        scope = scope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    ) {
    suspend fun hentOpplaringKategorisering(tiltakskode: Tiltakskode): OpplaringKategoriseringResponse =
        kategoriseringCache.getIfPresent(tiltakskode)
            ?: performGet("api/kodeverk/opplaring/kategorisering") {
                parameter("tiltakskode", tiltakskode)
            }.failIfNotSuccess("Kunne ikke hente opplæringkategorisering for tiltakskode $tiltakskode fra Mulighetsrommet")
                .body<OpplaringKategoriseringResponse>()
                .also { kategoriseringCache.put(tiltakskode, it) }

    suspend fun sertifiseringSok(term: String): List<SertifiseringResponse> = performGet("api/kodeverk/opplaring/sertifiseringer/sok") {
        parameter("q", term)
    }.failIfNotSuccess("Kunne ikke hente sertifiseringer fra Mulighetsrommet").body()
}
