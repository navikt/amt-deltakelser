package no.nav.tiltaksarrangor.client

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.requiredBody

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
    } catch (e: RestClientException) {
        throw OAuth2AuthorizationException(
            OAuth2Error("invalid_token_response"),
            e,
        )
    }

    private data class TokenExchangeRequest(
        @param:JsonProperty("identity_provider")
        val identityProvider: String,
        @param:JsonProperty("target")
        val target: String,
        @param:JsonProperty("user_token")
        val userToken: String,
        @param:JsonProperty("skip_cache")
        val skipCache: Boolean,
    )

    data class TexasTokenExchangeResult(
        @param:JsonProperty("access_token")
        val accessToken: String,
        @param:JsonProperty("expires_in")
        val expiresIn: Long,
    )

    private companion object {
        const val TOKENX_IDENTITY_PROVIDER = "tokenx"
    }
}
