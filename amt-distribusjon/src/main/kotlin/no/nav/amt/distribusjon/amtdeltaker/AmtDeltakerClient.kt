package no.nav.amt.distribusjon.amtdeltaker

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.auth.AzureAdTokenClient
import java.util.UUID

class AmtDeltakerClient(
    private val httpClient: HttpClient,
    private val azureAdTokenClient: AzureAdTokenClient,
    environment: Environment,
) {
    private val url = environment.amtDeltakerUrl
    private val scope = environment.amtDeltakerScope

    suspend fun getDeltaker(deltakerId: UUID): AmtDeltakerResponse {
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val response = httpClient.get("$url/deltaker/$deltakerId") {
            header(HttpHeaders.Authorization, token)
        }

        if (!response.status.isSuccess()) {
            error(
                "Kunne ikke hente deltaker fra amt-deltaker. " +
                    "Status=${response.status.value} error=${response.bodyAsText()}",
            )
        }
        return response.body()
    }
}
