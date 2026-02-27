package no.nav.amt.deltaker.apiclients.mulighetsrommet

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import java.util.UUID

class MulighetsrommetApiClient(
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
    suspend fun hentGjennomforingV2(id: UUID): GjennomforingV2KafkaPayload = performGet("api/v2/tiltaksgjennomforinger/$id")
        .failIfNotSuccess("Klarte ikke å hente gjennomføring $id fra Mulighetsrommet v2 API.")
        .body()
}
