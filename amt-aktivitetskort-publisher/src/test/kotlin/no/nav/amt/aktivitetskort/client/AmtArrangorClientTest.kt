package no.nav.amt.aktivitetskort.client

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.util.UUID

@RestClientTest(AmtArrangorClient::class)
@TestPropertySource(properties = ["amt.arrangor.url=http://arrangor"])
class AmtArrangorClientTest(
    private val sut: AmtArrangorClient,
) : RestClientTestBase() {
    @Test
    fun `hentArrangor - arrangor finnes - parser response og returnerer arrangor`() {
        val arrangorId = UUID.randomUUID()
        val overordnetArrangorId = UUID.randomUUID()
        val orgnummer = "123456789"

        server
            .expect(requestTo("http://arrangor/api/service/arrangor/organisasjonsnummer/$orgnummer"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN_IN_TEST"))
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
    fun `hentArrangor - arrangor finnes ikke - kaster RuntimeException`() {
        server
            .expect(requestTo("http://arrangor/api/service/arrangor/organisasjonsnummer/foo"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN_IN_TEST"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThrows<RuntimeException> {
            sut.hentArrangor("foo")
        }
    }

    @Test
    fun `hentArrangor - by id - arrangør finnes - parser response`() {
        val arrangorId = UUID.randomUUID()

        server
            .expect(requestTo("http://arrangor/api/service/arrangor/$arrangorId"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN_IN_TEST"))
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
}
