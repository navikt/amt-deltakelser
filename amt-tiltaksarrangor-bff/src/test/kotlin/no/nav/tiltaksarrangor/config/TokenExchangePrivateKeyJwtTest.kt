package no.nav.tiltaksarrangor.config

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

/**
 * Verifiserer at SecurityConfig sin authorizedClientManager håndterer private_key_jwt
 * for TokenX uten å kaste IllegalArgumentException fra validateClientAuthenticationMethod.
 *
 * Denne testen fanger feilen som oppstår hvis addParametersConverter ikke kalles
 * på RestClientTokenExchangeTokenResponseClient.
 */
class TokenExchangePrivateKeyJwtTest {
    @Test
    fun `token exchange med private_key_jwt kaster ikke IllegalArgumentException`() {
        val registrationRepo = InMemoryClientRegistrationRepository(TOKENX_REGISTRATION)
        val manager = SecurityConfig().authorizedClientManager(registrationRepo)

        // Uten addParametersConverter ville dette kastet:
        // IllegalArgumentException: This class supports `client_secret_basic`, `client_secret_post`,
        // and `none` by default. Client [amt-arrangor-tokenx] is using [private_key_jwt] instead.
        //
        // IllegalArgumentException kastes FØR nettverkskallet — så fravær av den
        // betyr at private_key_jwt-konfigurasjonen er korrekt.
        val result = runCatching { manager.authorize(AUTHORIZE_REQUEST) }

        result.exceptionOrNull()?.shouldNotBeClientAuthMethodError()
    }

    @Test
    fun `fanger feil dersom addParametersConverter mangler`() {
        val registrationRepo = InMemoryClientRegistrationRepository(TOKENX_REGISTRATION)

        val brokenManager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
            registrationRepo,
            InMemoryOAuth2AuthorizedClientService(registrationRepo),
        ).apply {
            setAuthorizedClientProvider(TokenExchangeOAuth2AuthorizedClientProvider())
        }

        val error = runCatching { brokenManager.authorize(AUTHORIZE_REQUEST) }.exceptionOrNull()

        // Denne SKAL kaste IllegalArgumentException — beviser at testen faktisk fanger problemet
        error.shouldBeInstanceOf<IllegalArgumentException>()
        error.message!!.contains("private_key_jwt") shouldBe true
    }

    private fun Throwable.shouldNotBeClientAuthMethodError() {
        val message = this.message ?: ""
        if (this is IllegalArgumentException && message.contains("client_secret_basic")) {
            throw AssertionError(
                "SecurityConfig mangler addParametersConverter for private_key_jwt. " +
                    "Token exchange vil feile i produksjon med: $message",
                this,
            )
        }
    }

    companion object {
        private val RSA_KEY = RSAKeyGenerator(2048).keyID("test-key").generate()

        private val TOKENX_REGISTRATION = ClientRegistration
            .withRegistrationId("amt-arrangor-tokenx")
            .clientId("amt-tiltaksarrangor-bff")
            .clientSecret(RSA_KEY.toJSONString())
            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
            .authorizationGrantType(AuthorizationGrantType("urn:ietf:params:oauth:grant-type:token-exchange"))
            .scope("amt-arrangor-client-id")
            .tokenUri("http://localhost:9999/tokenx/token")
            .build()

        private val AUTHORIZE_REQUEST = OAuth2AuthorizeRequest
            .withClientRegistrationId("amt-arrangor-tokenx")
            .principal(
                JwtAuthenticationToken(
                    Jwt
                        .withTokenValue("subject-token")
                        .header("alg", "RS256")
                        .issuer("http://localhost:9999/tokenx")
                        .subject("test-user")
                        .claim("pid", "12345678901")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build(),
                ),
            ).build()
    }
}
