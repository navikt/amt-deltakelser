package no.nav.tiltaksarrangor.client

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

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
}
