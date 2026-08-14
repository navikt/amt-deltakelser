package no.nav.tiltaksarrangor.config

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_TOKENX_CLIENT_ID
import org.hamcrest.CoreMatchers.containsString
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
 * Tester OAuth2ClientConfig. Kan fjernes når det er verifisert at tokenx fungerer som forventet.
 */
class OAuth2ClientConfigTest {
    @Test
    fun `token exchange sender korrekt request til TokenX`() {
        val restClientBuilder = RestClient
            .builder()
            .configureMessageConverters { builder ->
                builder.addCustomConverter(OAuth2AccessTokenResponseHttpMessageConverter())
            }

        val server = MockRestServiceServer.bindTo(restClientBuilder).build()

        server
            .expect(requestTo(TOKEN_URI))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().string(
                    containsString(
                        "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange",
                    ),
                ),
            ).andExpect(
                content().string(
                    containsString(
                        "subject_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Ajwt",
                    ),
                ),
            ).andExpect(
                content().string(
                    containsString(
                        "audience=$AMT_ARRANGOR_TOKENX_CLIENT_ID",
                    ),
                ),
            ).andExpect(content().string(containsString("subject_token=")))
            .andExpect(content().string(containsString("client_assertion=")))
            .andRespond(
                withSuccess(
                    TOKEN_RESPONSE_JSON,
                    MediaType.APPLICATION_JSON,
                ),
            )

        val client = tokenExchangeClient(restClientBuilder)

        val provider = TokenExchangeOAuth2AuthorizedClientProvider().apply {
            setAccessTokenResponseClient(client)
        }

        val registrationRepository = InMemoryClientRegistrationRepository(TOKENX_REGISTRATION)

        val manager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
            registrationRepository,
            InMemoryOAuth2AuthorizedClientService(registrationRepository),
        ).apply {
            setAuthorizedClientProvider(provider)
        }

        val authorizedClient = manager.authorize(
            OAuth2AuthorizeRequest
                .withClientRegistrationId("amt-arrangor-tokenx")
                .principal(jwtAuthentication())
                .build(),
        )

        authorizedClient.shouldNotBeNull()
        authorizedClient.accessToken.tokenValue shouldBe "tokenx-test-token"

        server.verify()
    }

    private fun tokenExchangeClient(builder: RestClient.Builder): RestClientTokenExchangeTokenResponseClient =
        RestClientTokenExchangeTokenResponseClient().apply {
            addParametersConverter { grantRequest ->
                org.springframework.util.LinkedMultiValueMap<String, String>().apply {
                    add("audience", grantRequest.clientRegistration.scopes.single())
                }
            }

            addParametersConverter { grantRequest ->
                NimbusJwtClientAuthenticationParametersConverter<TokenExchangeGrantRequest> { registration ->
                    JWK.parse(registration.clientSecret)
                }.convert(grantRequest)!!
            }

            setRestClient(builder.build())
        }

    private fun jwtAuthentication(): JwtAuthenticationToken = JwtAuthenticationToken(
        Jwt
            .withTokenValue(SUBJECT_TOKEN)
            .header("alg", "RS256")
            .issuer("http://localhost:9999/tokenx")
            .subject("test-user")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build(),
    )

    companion object {
        private const val TOKEN_URI = "http://tokenx/token"
        private const val SUBJECT_TOKEN = "subject-token"

        private val RSA_KEY =
            RSAKeyGenerator(2048)
                .keyID("test-key")
                .generate()

        private val TOKENX_REGISTRATION =
            ClientRegistration
                .withRegistrationId("amt-arrangor-tokenx")
                .clientId("amt-tiltaksarrangor-bff")
                .clientSecret(RSA_KEY.toJSONString())
                .clientAuthenticationMethod(
                    ClientAuthenticationMethod.PRIVATE_KEY_JWT,
                ).authorizationGrantType(
                    AuthorizationGrantType(
                        "urn:ietf:params:oauth:grant-type:token-exchange",
                    ),
                ).scope(AMT_ARRANGOR_TOKENX_CLIENT_ID)
                .tokenUri(TOKEN_URI)
                .build()

        private val TOKEN_RESPONSE_JSON =
            """
            {
              "access_token": "tokenx-test-token",
              "token_type": "Bearer",
              "expires_in": 3600,
              "scope": "$AMT_ARRANGOR_TOKENX_CLIENT_ID"
            }
            """.trimIndent()
    }
}
