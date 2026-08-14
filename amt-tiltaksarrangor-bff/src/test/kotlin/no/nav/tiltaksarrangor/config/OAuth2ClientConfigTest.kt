package no.nav.tiltaksarrangor.config

import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.security.oauth2.client.endpoint.AbstractRestClientOAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.util.MultiValueMap
import java.time.Instant

class OAuth2ClientConfigTest {
    private val config = OAuth2ClientConfig()

    @Test
    fun `skal sette audience og client assertion ved token exchange`() {
        // Arrange
        val grantRequest = tokenExchangeGrantRequest(scopes = setOf("dev-gcp:amt:downstream"))

        // Act
        val parameters = requireNotNull(tokenExchangeParametersConverter().convert(grantRequest))

        // Assert
        parameters.getFirst("audience") shouldBe "dev-gcp:amt:downstream"
        val clientAssertion = parameters.getFirst("client_assertion")
        clientAssertion.shouldNotBeBlank()
        parameters.getFirst("client_assertion_type") shouldBe "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
    }

    @Test
    fun `skal sette not before fem sekunder bak issued at i client assertion`() {
        // Arrange
        val grantRequest = tokenExchangeGrantRequest(scopes = setOf("dev-gcp:amt:downstream"))

        // Act
        val parameters = requireNotNull(tokenExchangeParametersConverter().convert(grantRequest))
        val jwt = SignedJWT.parse(parameters.getFirst("client_assertion"))
        val nbf = jwt.jwtClaimsSet.notBeforeTime.toInstant()
        val iat = jwt.jwtClaimsSet.issueTime.toInstant()

        // Assert
        nbf.plusSeconds(4).isBefore(iat) shouldBe true
    }

    @Test
    fun `skal feile nar token exchange klient ikke har nøyaktig ett scope`() {
        // Arrange
        val withoutScope = tokenExchangeGrantRequest(scopes = emptySet())
        val withMultipleScopes = tokenExchangeGrantRequest(scopes = setOf("scope-a", "scope-b"))
        val expectedMessage =
            "Expected exactly one scope for token exchange audience in client registration 'amt-arrangor-tokenx'"

        // Act + Assert
        shouldThrow<IllegalArgumentException> {
            tokenExchangeParametersConverter().convert(withoutScope)
        }.message shouldBe expectedMessage

        shouldThrow<IllegalArgumentException> {
            tokenExchangeParametersConverter().convert(withMultipleScopes)
        }.message shouldBe expectedMessage
    }

    private fun tokenExchangeParametersConverter(): Converter<TokenExchangeGrantRequest, MultiValueMap<String, String>> {
        val responseClient = tokenExchangeResponseClient()
        val field = AbstractRestClientOAuth2AccessTokenResponseClient::class.java.getDeclaredField("parametersConverter")
        field.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        return field.get(responseClient) as Converter<TokenExchangeGrantRequest, MultiValueMap<String, String>>
    }

    private fun tokenExchangeResponseClient(): RestClientTokenExchangeTokenResponseClient {
        val method = OAuth2ClientConfig::class.java.getDeclaredMethod("tokenExchangeResponseClient")
        method.isAccessible = true
        return method.invoke(config) as RestClientTokenExchangeTokenResponseClient
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
