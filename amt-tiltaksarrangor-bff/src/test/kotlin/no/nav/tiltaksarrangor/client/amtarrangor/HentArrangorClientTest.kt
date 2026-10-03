package no.nav.tiltaksarrangor.client.amtarrangor

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_AAD_CLIENT_ID
import no.nav.tiltaksarrangor.client.RestClientTestBase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.io.IOException
import java.util.UUID

@RestClientTest(HentArrangorClient::class)
class HentArrangorClientTest(
    private val sut: HentArrangorClient,
) : RestClientTestBase(AMT_ARRANGOR_AAD_CLIENT_ID) {
    @Nested
    inner class GetArrangorTests {
        @Test
        fun `getArrangor - returnerer arrangor med overordnet arrangor ved suksess`() {
            val orgnummer = "123456789"
            val arrangorId = UUID.randomUUID()
            val overordnetArrangorId = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-arrangor-aad/$orgnummer"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer amt-arrangor-aad-token"))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "id": "$arrangorId",
                          "navn": "Test Arrangør AS",
                          "organisasjonsnummer": "$orgnummer",
                          "overordnetArrangor": {
                            "id": "$overordnetArrangorId",
                            "navn": "Overordnet AS",
                            "organisasjonsnummer": "987654321",
                            "overordnetArrangorId": null
                          }
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.getArrangor(orgnummer)

            assertSoftly(result.shouldNotBeNull()) {
                id shouldBe arrangorId
                navn shouldBe "Test Arrangør AS"
                organisasjonsnummer shouldBe orgnummer
                overordnetArrangor shouldNotBe null
                overordnetArrangor!!.id shouldBe overordnetArrangorId
                overordnetArrangor.navn shouldBe "Overordnet AS"
            }
        }

        @Test
        fun `getArrangor - returnerer arrangor uten overordnet arrangor`() {
            val orgnummer = "123456789"
            val arrangorId = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-arrangor-aad/$orgnummer"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer amt-arrangor-aad-token"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "id": "$arrangorId",
                          "navn": "Test Arrangør AS",
                          "organisasjonsnummer": "$orgnummer",
                          "overordnetArrangor": null
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.getArrangor(orgnummer)

            result shouldNotBe null
            assertSoftly(result.shouldNotBeNull()) {
                id shouldBe arrangorId
                overordnetArrangor shouldBe null
            }
        }

        @Test
        fun `getArrangor - kaster NoSuchElementException ved 404`() {
            val orgnummer = "123456789"

            server
                .expect(requestTo("http://amt-arrangor-aad/$orgnummer"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer amt-arrangor-aad-token"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND))

            shouldThrow<NoSuchElementException> {
                sut.getArrangor(orgnummer)
            }.message shouldBe "Arrangør med orgnummer $orgnummer finnes ikke hos amt-arrangor."
        }

        @Test
        fun `getArrangor - kaster upstream exception ved 403`() {
            val orgnummer = "123456789"

            server
                .expect(requestTo("http://amt-arrangor-aad/$orgnummer"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer amt-arrangor-aad-token"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            shouldThrow<UpstreamServiceException> {
                sut.getArrangor(orgnummer)
            }.statusCode shouldBe 403
        }

        @Test
        fun `getArrangor - kaster upstream exception ved 401`() {
            val orgnummer = "123456789"

            server
                .expect(requestTo("http://amt-arrangor-aad/$orgnummer"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer amt-arrangor-aad-token"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

            shouldThrow<UpstreamServiceException> {
                sut.getArrangor(orgnummer)
            }.statusCode shouldBe 401
        }

        @Test
        fun `getArrangor - kaster retryable exception ved 500`() {
            val orgnummer = "123456789"

            server
                .expect(requestTo("http://amt-arrangor-aad/$orgnummer"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer amt-arrangor-aad-token"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            shouldThrow<RetryableUpstreamServiceException> {
                sut.getArrangor(orgnummer)
            }.message shouldBe "Kall mot amt-arrangor-aad feilet under hente arrangør (HTTP 500)"
        }

        @Test
        fun `getArrangor - kaster retryable exception ved nettverksfeil`() {
            val orgnummer = "123456789"

            server
                .expect(requestTo("http://amt-arrangor-aad/$orgnummer"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer amt-arrangor-aad-token"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withException(IOException("boom")))

            shouldThrow<RetryableUpstreamServiceException> {
                sut.getArrangor(orgnummer)
            }.message shouldBe "Kall mot amt-arrangor-aad feilet under hente arrangør"
        }
    }
}
