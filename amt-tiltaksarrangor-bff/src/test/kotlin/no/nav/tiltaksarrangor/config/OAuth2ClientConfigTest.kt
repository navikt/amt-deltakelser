package no.nav.tiltaksarrangor.config

import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.tiltaksarrangor.service.TexasTokenExchangeClient
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.security.oauth2.client.endpoint.AbstractRestClientOAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.util.MultiValueMap
import java.time.Instant

class OAuth2ClientConfigTest {
    private val texasTokenExchangeClient = mockk<TexasTokenExchangeClient>()

    @Test
    fun `skal hente token via texas ved token exchange nar toggle er pa`() {
        val config = OAuth2ClientConfig(texasTokenExchangeClient, true)
        every {
            texasTokenExchangeClient.exchangeToken(
                userToken = "subject-token",
                target = "dev-gcp:amt:downstream",
                skipCache = false,
            )
        } returns TexasTokenExchangeClient.TexasTokenExchangeResult(accessToken = "obo-token", expiresIn = 3599)

        val response = tokenExchangeResponseClient(config).getTokenResponse(
            tokenExchangeGrantRequest(scopes = setOf("dev-gcp:amt:downstream")),
        )

        response.accessToken.tokenValue shouldBe "obo-token"
        verify {
            texasTokenExchangeClient.exchangeToken(
                userToken = "subject-token",
                target = "dev-gcp:amt:downstream",
                skipCache = false,
            )
        }
    }

    @Test
    fun `skal bruke legacy token exchange nar toggle er av`() {
        val config = OAuth2ClientConfig(texasTokenExchangeClient, false)
        val parameters = requireNotNull(
            legacyTokenExchangeParametersConverter(config).convert(
                tokenExchangeGrantRequest(scopes = setOf("dev-gcp:amt:downstream")),
            ),
        )
        val clientAssertion = parameters.getFirst("client_assertion")
        clientAssertion.shouldNotBeBlank()
        val jwt = SignedJWT.parse(clientAssertion)
        val nbf = jwt.jwtClaimsSet.notBeforeTime.toInstant()
        val iat = jwt.jwtClaimsSet.issueTime.toInstant()
        parameters.getFirst("audience") shouldBe "dev-gcp:amt:downstream"

        nbf.plusSeconds(4).isBefore(iat) shouldBe true
    }

    @Test
    fun `skal feile nar token exchange klient ikke har nøyaktig ett scope`() {
        val config = OAuth2ClientConfig(texasTokenExchangeClient, true)
        val withoutScope = tokenExchangeGrantRequest(scopes = emptySet())
        val withMultipleScopes = tokenExchangeGrantRequest(scopes = setOf("scope-a", "scope-b"))
        val expectedMessage =
            "Expected exactly one scope for token exchange audience in client registration 'amt-arrangor-tokenx'"

        shouldThrow<IllegalArgumentException> {
            tokenExchangeResponseClient(config).getTokenResponse(withoutScope)
        }.message shouldBe expectedMessage

        shouldThrow<IllegalArgumentException> {
            tokenExchangeResponseClient(config).getTokenResponse(withMultipleScopes)
        }.message shouldBe expectedMessage
    }

    @Suppress("UNCHECKED_CAST")
    private fun tokenExchangeResponseClient(config: OAuth2ClientConfig): OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest> {
        val method = OAuth2ClientConfig::class.java.getDeclaredMethod("tokenExchangeResponseClient")
        method.isAccessible = true
        return method.invoke(config) as OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest>
    }

    private fun legacyTokenExchangeParametersConverter(
        config: OAuth2ClientConfig,
    ): Converter<TokenExchangeGrantRequest, MultiValueMap<String, String>> {
        val responseClient = tokenExchangeResponseClient(config) as RestClientTokenExchangeTokenResponseClient
        val field = AbstractRestClientOAuth2AccessTokenResponseClient::class.java.getDeclaredField("parametersConverter")
        field.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        return field.get(responseClient) as Converter<TokenExchangeGrantRequest, MultiValueMap<String, String>>
    }

    private fun tokenExchangeGrantRequest(scopes: Set<String>): TokenExchangeGrantRequest {
        val clientRegistrationBuilder = ClientRegistration
            .withRegistrationId("amt-arrangor-tokenx")
            .clientId("test-client")
            .clientSecret(readJwk())
            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
            .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
            .tokenUri("https://tokenx.example/token")

        if (scopes.isNotEmpty()) {
            clientRegistrationBuilder.scope(*scopes.toTypedArray())
        }

        val accessToken = OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "subject-token",
            Instant.now().minusSeconds(10),
            Instant.now().plusSeconds(60),
        )

        return TokenExchangeGrantRequest(clientRegistrationBuilder.build(), accessToken, null)
    }

    private fun readJwk(): String =
        requireNotNull(javaClass.getResource("/jwk.json")) { "Fant ikke src/test/resources/jwk.json" }.readText()
}
