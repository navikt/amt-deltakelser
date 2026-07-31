package no.nav.tiltaksarrangor.config

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Instant

/**
 * Verifiserer at SecurityConfig sin authorizedClientManager:
 * 1. Håndterer private_key_jwt uten å kaste IllegalArgumentException
 * 2. Sender audience-parameteren som TokenX krever (prod-feil: "Parameter audience missing")
 */
class TokenExchangePrivateKeyJwtTest {
    @Test
    fun `token exchange med private_key_jwt kaster ikke IllegalArgumentException`() {
        val registrationRepo = InMemoryClientRegistrationRepository(TOKENX_REGISTRATION)
        val manager = SecurityConfig().authorizedClientManager(registrationRepo)

        // Uten addParametersConverter ville dette kastet:
        // IllegalArgumentException: This class supports `client_secret_basic`, `client_secret_post`,
        // and `none` by default. Client [amt-arrangor-tokenx] is using [private_key_jwt] instead.
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

        error.shouldBeInstanceOf<IllegalArgumentException>()
        error.message!! shouldContain "private_key_jwt"
    }

    @Test
    fun `tokenExchangeResponseClient sender audience i token exchange request`() {
        val restClientBuilder = RestClient
            .builder()
            .configureMessageConverters { it.addCustomConverter(OAuth2AccessTokenResponseHttpMessageConverter()) }
        val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()

        mockServer
            .expect(requestTo(TOKEN_URI))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("audience=$EXPECTED_AUDIENCE")))
            .andRespond(withSuccess(TOKEN_RESPONSE_JSON, MediaType.APPLICATION_JSON))

        val tokenExchangeClient = SecurityConfig().tokenExchangeResponseClient()
        tokenExchangeClient.setRestClient(restClientBuilder.build())

        val provider = TokenExchangeOAuth2AuthorizedClientProvider()
        provider.setAccessTokenResponseClient(tokenExchangeClient)

        val registrationRepo = InMemoryClientRegistrationRepository(TOKENX_REGISTRATION)
        val manager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
            registrationRepo,
            InMemoryOAuth2AuthorizedClientService(registrationRepo),
        ).apply { setAuthorizedClientProvider(provider) }

        manager.authorize(AUTHORIZE_REQUEST)

        mockServer.verify()
    }

    @Test
    fun `uten audience-converter feiler TokenX med parameter audience missing`() {
        val restClientBuilder = RestClient
            .builder()
            .configureMessageConverters { it.addCustomConverter(OAuth2AccessTokenResponseHttpMessageConverter()) }
        val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()

        mockServer
            .expect(requestTo(TOKEN_URI))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(not(containsString("audience="))))
            .andRespond(withSuccess(TOKEN_RESPONSE_JSON, MediaType.APPLICATION_JSON))

        // Klient MED private_key_jwt-støtte, men UTEN audience-converter — reproduserer prod-feilen
        val brokenClient = RestClientTokenExchangeTokenResponseClient().apply {
            addParametersConverter { grantRequest ->
                NimbusJwtClientAuthenticationParametersConverter<TokenExchangeGrantRequest> { registration ->
                    JWK.parse(registration.clientSecret)
                }.convert(grantRequest)!!
            }
            setRestClient(restClientBuilder.build())
        }

        val provider = TokenExchangeOAuth2AuthorizedClientProvider()
        provider.setAccessTokenResponseClient(brokenClient)

        val registrationRepo = InMemoryClientRegistrationRepository(TOKENX_REGISTRATION)
        val manager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
            registrationRepo,
            InMemoryOAuth2AuthorizedClientService(registrationRepo),
        ).apply { setAuthorizedClientProvider(provider) }

        manager.authorize(AUTHORIZE_REQUEST)

        mockServer.verify()
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
        private const val TOKEN_URI = "http://localhost:9999/tokenx/token"
        private const val EXPECTED_AUDIENCE = "amt-arrangor-client-id"

        private val RSA_KEY = RSAKeyGenerator(2048).keyID("test-key").generate()

        private val TOKENX_REGISTRATION = ClientRegistration
            .withRegistrationId("amt-arrangor-tokenx")
            .clientId("amt-tiltaksarrangor-bff")
            .clientSecret(RSA_KEY.toJSONString())
            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
            .authorizationGrantType(AuthorizationGrantType("urn:ietf:params:oauth:grant-type:token-exchange"))
            .scope(EXPECTED_AUDIENCE)
            .tokenUri(TOKEN_URI)
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

        private const val TOKEN_RESPONSE_JSON =
            """{"access_token":"eyJhbGciOiJSUzI1NiJ9.test","token_type":"Bearer","expires_in":3600,"scope":"amt-arrangor-client-id"}"""
    }
}
