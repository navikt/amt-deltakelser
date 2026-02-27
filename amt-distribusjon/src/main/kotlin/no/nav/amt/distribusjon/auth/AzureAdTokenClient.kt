package no.nav.amt.distribusjon.auth

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import no.nav.amt.distribusjon.Environment
import org.slf4j.LoggerFactory
import java.time.Duration

class AzureAdTokenClient(
    private val httpClient: HttpClient,
    environment: Environment,
) {
    private val azureAdTokenUrl = environment.azureAdTokenUrl
    private val clientId = environment.azureClientId
    private val clientSecret = environment.azureClientSecret

    private val log = LoggerFactory.getLogger(javaClass)

    private val tokenCache = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofMinutes(55))
        .build<String, AzureAdToken>()

    suspend fun getMachineToMachineToken(scope: String): String {
        val token = tokenCache.getIfPresent(scope) ?: createMachineToMachineToken(scope)

        return "${token.tokenType} ${token.accessToken}"
    }

    private suspend fun createMachineToMachineToken(scope: String): AzureAdToken {
        val response = httpClient.post(azureAdTokenUrl) {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "client_credentials")
                        append("client_id", clientId)
                        append("client_secret", clientSecret)
                        append("scope", scope)
                    },
                ),
            )
        }

        if (!response.status.isSuccess()) {
            log.error("Kunne ikke hente AAD-token: ${response.status.value} ${response.bodyAsText()}")
            error("Kunne ikke hente AAD-token")
        }

        val token = response.body<AzureAdToken>()

        tokenCache.put(scope, token)

        return token
    }
}

@JsonNaming(SnakeCaseStrategy::class)
data class AzureAdToken(
    val tokenType: String,
    val accessToken: String,
)
