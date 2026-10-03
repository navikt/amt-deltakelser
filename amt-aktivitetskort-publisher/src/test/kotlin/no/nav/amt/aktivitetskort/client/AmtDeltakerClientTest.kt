package no.nav.amt.aktivitetskort.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import java.util.UUID

@RestClientTest(AmtDeltakerClient::class)
class AmtDeltakerClientTest(
    @Autowired private val sut: AmtDeltakerClient,
) : RestClientTestBase(AMT_DELTAKER_CLIENT_ID) {
    @Test
    fun `getDeltaker - manglende body ved 204 gir upstream-feil med statuskode`() {
        val deltakerId = UUID.randomUUID()
        server
            .expect(requestTo("http://amt-deltaker/deltaker/$deltakerId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))

        val exception = shouldThrow<UpstreamServiceException> {
            sut.getDeltaker(deltakerId)
        }

        exception.statusCode shouldBe 204
    }

    @Test
    fun `getDeltaker - kaster retrybar exception ved 500`() {
        val deltakerId = UUID.randomUUID()

        server
            .expect(requestTo("http://amt-deltaker/deltaker/$deltakerId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val thrown = shouldThrow<RetryableUpstreamServiceException> {
            sut.getDeltaker(deltakerId)
        }

        thrown.message shouldBe "Kall mot amt-deltaker feilet under hente deltaker (HTTP 500)"
    }
}
