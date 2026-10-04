package no.nav.amt.aktivitetskort.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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

@RestClientTest(AmtArrangorClient::class)
class AmtArrangorClientTest(
    @Autowired private val sut: AmtArrangorClient,
) : RestClientTestBase(AMT_ARRANGOR_CLIENT_ID) {
    @Nested
    inner class HentArrangorByOrgnummerTests {
        @Test
        fun `hentArrangor - arrangor finnes - parser response og returnerer arrangor`() {
            val arrangorId = UUID.randomUUID()
            val overordnetArrangorId = UUID.randomUUID()
            val orgnummer = "123456789"

            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/organisasjonsnummer/$orgnummer"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer amt-arrangor-token"))
                .andRespond(
                    withSuccess(
                        """{
                        "id": "$arrangorId",
                        "navn": "Test Arrangor",
                        "organisasjonsnummer": "$orgnummer",
                        "overordnetArrangor": {
                            "id": "$overordnetArrangorId",
                            "navn": "Overordnet",
                            "organisasjonsnummer": "987654321",
                            "overordnetArrangorId": null
                        }
                    }""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.hentArrangor(orgnummer)

            result.id shouldBe arrangorId
            result.organisasjonsnummer shouldBe orgnummer
            result.navn shouldBe "Test Arrangor"
            result.overordnetArrangor?.id shouldBe overordnetArrangorId
        }

        @Test
        fun `hentArrangor - manglende body ved 204 gir upstream-feil med statuskode`() {
            val orgnummer = "123456789"
            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/organisasjonsnummer/$orgnummer"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NO_CONTENT))

            val exception = shouldThrow<UpstreamServiceException> {
                sut.hentArrangor(orgnummer)
            }

            exception.statusCode shouldBe 204
        }

        @Test
        fun `hentArrangor - kaster ikke-retrybar exception ved 404`() {
            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/organisasjonsnummer/foo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND))

            shouldThrow<UpstreamServiceException> {
                sut.hentArrangor("foo")
            }
        }

        @Test
        fun `hentArrangor - kaster ikke-retrybar exception ved 401`() {
            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/organisasjonsnummer/foo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

            shouldThrow<UpstreamServiceException> {
                sut.hentArrangor("foo")
            }
        }

        @Test
        fun `hentArrangor - kaster ikke-retrybar exception ved 403`() {
            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/organisasjonsnummer/foo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            shouldThrow<UpstreamServiceException> {
                sut.hentArrangor("foo")
            }
        }

        @Test
        fun `hentArrangor - kaster retryable exception ved ResourceAccessException`() {
            val orgnummer = "123456789"

            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/organisasjonsnummer/$orgnummer"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(IOException("boom")))

            val thrown = shouldThrow<RetryableUpstreamServiceException> {
                sut.hentArrangor(orgnummer)
            }

            thrown.message shouldBe "Kall mot amt-arrangor feilet under hente arrangør med orgnummer"
        }
    }

    @Nested
    inner class HentArrangorByIdTests {
        @Test
        fun `hentArrangor - skal sende Nav-Consumer-Id og Accept-header`() {
            val arrangorId = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/$arrangorId"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Nav-Consumer-Id", "amt-aktivitetskort-publisher"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(
                    withSuccess(
                        """{"id":"$arrangorId","navn":"Test","organisasjonsnummer":"123","overordnetArrangor":null}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            sut.hentArrangor(arrangorId)
            server.verify()
        }

        @Test
        fun `hentArrangor - arrangør finnes - parser response`() {
            val arrangorId = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/$arrangorId"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess(
                        """{
                        "id": "$arrangorId",
                        "navn": "Test Arrangor",
                        "organisasjonsnummer": "123456789",
                        "overordnetArrangor": null
                    }""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            val result = sut.hentArrangor(arrangorId)

            result.id shouldBe arrangorId
            result.navn shouldBe "Test Arrangor"
        }

        @Test
        fun `hentArrangor - manglende body ved 204 gir upstream-feil med statuskode`() {
            val arrangorId = UUID.randomUUID()
            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/$arrangorId"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NO_CONTENT))

            val exception = shouldThrow<UpstreamServiceException> {
                sut.hentArrangor(arrangorId)
            }

            exception.statusCode shouldBe 204
        }

        @Test
        fun `hentArrangor - kaster ikke-retrybar exception ved 401`() {
            val arrangorId = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/$arrangorId"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

            shouldThrow<UpstreamServiceException> {
                sut.hentArrangor(arrangorId)
            }
        }

        @Test
        fun `hentArrangor - kaster ikke-retrybar exception ved 403`() {
            val arrangorId = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/$arrangorId"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            shouldThrow<UpstreamServiceException> {
                sut.hentArrangor(arrangorId)
            }
        }

        @Test
        fun `hentArrangor - kaster retryable exception ved 500`() {
            val arrangorId = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/$arrangorId"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            val thrown = shouldThrow<RetryableUpstreamServiceException> {
                sut.hentArrangor(arrangorId)
            }

            thrown.message shouldBe "Kall mot amt-arrangor feilet under hente arrangør med id (HTTP 500)"
        }

        @Test
        fun `hentArrangor - kaster retryable exception ved ResourceAccessException`() {
            val arrangorId = UUID.randomUUID()

            server
                .expect(requestTo("http://amt-arrangor/api/service/arrangor/$arrangorId"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(IOException("boom")))

            val thrown = shouldThrow<RetryableUpstreamServiceException> {
                sut.hentArrangor(arrangorId)
            }

            thrown.message shouldBe "Kall mot amt-arrangor feilet under hente arrangør med id"
        }
    }
}
