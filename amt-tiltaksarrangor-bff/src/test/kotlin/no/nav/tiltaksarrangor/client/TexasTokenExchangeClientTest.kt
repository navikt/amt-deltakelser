package no.nav.tiltaksarrangor.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.io.IOException

@RestClientTest(TexasTokenExchangeClient::class)
@TestPropertySource(properties = ["NAIS_TOKEN_EXCHANGE_ENDPOINT=http://texas-token-exchange"])
class TexasTokenExchangeClientTest(
    @Autowired private val server: MockRestServiceServer,
    @Autowired private val sut: TexasTokenExchangeClient,
) {
    @Test
    fun `exchangeToken - sender token exchange request og returnerer access token`() {
        server
            .expect(requestTo("http://texas-token-exchange"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(
                content().json(
                    """
                    {
                      "identity_provider": "tokenx",
                      "target": "dev-gcp:amt:downstream",
                      "user_token": "subject-token",
                      "skip_cache": false
                    }
                    """.trimIndent(),
                ),
            ).andRespond(
                withSuccess(
                    """
                    {
                      "access_token": "obo-token",
                      "expires_in": 3599
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = sut.exchangeToken(
            userToken = "subject-token",
            target = "dev-gcp:amt:downstream",
        )

        result.accessToken shouldBe "obo-token"
        result.expiresIn shouldBe 3599
        server.verify()
    }

    @Test
    fun `exchangeToken - kaster OAuth2AuthorizationException ved 403`() {
        server
            .expect(requestTo("http://texas-token-exchange"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.FORBIDDEN).body("forbidden"))

        val exception = shouldThrow<OAuth2AuthorizationException> {
            sut.exchangeToken(
                userToken = "subject-token",
                target = "dev-gcp:amt:downstream",
            )
        }

        exception.error.errorCode shouldBe "invalid_token_response"
        exception.error.description shouldBe "Texas token exchange feilet. Status=403"

        server.verify()
    }

    @Test
    fun `exchangeToken - kaster OAuth2AuthorizationException ved 500`() {
        server
            .expect(requestTo("http://texas-token-exchange"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"))

        val exception = shouldThrow<OAuth2AuthorizationException> {
            sut.exchangeToken(
                userToken = "subject-token",
                target = "dev-gcp:amt:downstream",
            )
        }

        exception.error.errorCode shouldBe "invalid_token_response"
        exception.error.description shouldBe "Texas token exchange feilet. Status=500"

        server.verify()
    }

    @Test
    fun `exchangeToken - kaster OAuth2AuthorizationException ved transportfeil`() {
        server
            .expect(requestTo("http://texas-token-exchange"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withException(IOException("boom")))

        val exception = shouldThrow<OAuth2AuthorizationException> {
            sut.exchangeToken(
                userToken = "subject-token",
                target = "dev-gcp:amt:downstream",
            )
        }

        exception.error.errorCode shouldBe "invalid_token_response"
        exception.error.description shouldBe "Texas token exchange feilet før HTTP-respons ble mottatt"

        server.verify()
    }

    @Test
    fun `exchangeToken - kaster OAuth2AuthorizationException ved ugyldig responsformat`() {
        server
            .expect(requestTo("http://texas-token-exchange"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    "not-json",
                    MediaType.TEXT_PLAIN,
                ),
            )

        val exception = shouldThrow<OAuth2AuthorizationException> {
            sut.exchangeToken(
                userToken = "subject-token",
                target = "dev-gcp:amt:downstream",
            )
        }

        exception.error.errorCode shouldBe "invalid_token_response"
        exception.error.description shouldBe "Texas token exchange returnerte ugyldig respons"

        server.verify()
    }
}
