package no.nav.tiltaksarrangor.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.requiredBody
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@Service
class TexasTokenExchangeClient(
    @Value($$"${NAIS_TOKEN_EXCHANGE_ENDPOINT:${TOKEN_X_TOKEN_ENDPOINT}}")
    private val tokenExchangeEndpoint: String,
    builder: RestClient.Builder,
) {
    private val restClient = builder.build()

    fun exchangeToken(
        userToken: String,
        target: String,
        skipCache: Boolean = false,
    ): TexasTokenExchangeResult = try {
        restClient
            .post()
            .uri(tokenExchangeEndpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                TokenExchangeRequest(
                    identityProvider = TOKENX_IDENTITY_PROVIDER,
                    target = target,
                    userToken = userToken,
                    skipCache = skipCache,
                ),
            ).retrieve()
            .requiredBody()
    } catch (e: RestClientResponseException) {
        throw invalidTokenResponseException(
            description = "Texas token exchange feilet. Status=${e.statusCode.value()}",
            cause = e,
        )
    } catch (e: RestClientException) {
        throw invalidTokenResponseException(
            description = "Texas token exchange feilet før HTTP-respons ble mottatt",
            cause = e,
        )
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    private data class TokenExchangeRequest(
        val identityProvider: String,
        val target: String,
        val userToken: String,
        val skipCache: Boolean,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class TexasTokenExchangeResult(
        val accessToken: String,
        val expiresIn: Long,
    )

    private companion object {
        const val TOKENX_IDENTITY_PROVIDER = "tokenx"
    }

    private fun invalidTokenResponseException(
        description: String,
        cause: Throwable,
    ): OAuth2AuthorizationException = OAuth2AuthorizationException(
        OAuth2Error("invalid_token_response", description, null),
        cause,
    )
}
