package no.nav.amt.lib.spring.boot.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException

class UpstreamServiceExceptionTest {
    @Test
    fun `5xx-respons blir retrybar uten upstream-melding i exception-meldingen`() {
        val cause = HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "respons inneholder sensitive data")

        val exception = shouldThrow<RetryableUpstreamServiceException> {
            executeUpstreamCall(
                serviceName = "Kodeverk",
                operation = "hent postnummer",
            ) { throw cause }
        }

        exception.serviceName shouldBe "Kodeverk"
        exception.operation shouldBe "hent postnummer"
        exception.statusCode shouldBe 500
        exception.cause shouldBe cause
        exception.message shouldBe "Kall mot Kodeverk feilet under hent postnummer (HTTP 500)"
    }

    @Test
    fun `4xx-respons blir ikke retrybar`() {
        val cause = HttpClientErrorException(HttpStatus.BAD_REQUEST)

        val exception = shouldThrow<UpstreamServiceException> {
            executeUpstreamCall(
                serviceName = "Kodeverk",
                operation = "hent postnummer",
            ) { throw cause }
        }

        exception::class shouldBe UpstreamServiceException::class
        exception.serviceName shouldBe "Kodeverk"
        exception.operation shouldBe "hent postnummer"
        exception.statusCode shouldBe 400
        exception.cause shouldBe cause
    }

    @Test
    fun `501-respons blir ikke retrybar`() {
        val cause = HttpServerErrorException(HttpStatus.NOT_IMPLEMENTED)

        val exception = shouldThrow<UpstreamServiceException> {
            executeUpstreamCall(
                serviceName = "Kodeverk",
                operation = "hent postnummer",
            ) { throw cause }
        }

        exception::class shouldBe UpstreamServiceException::class
        exception.statusCode shouldBe 501
    }

    @Test
    fun `nettverksfeil blir retrybar`() {
        val cause = ResourceAccessException("connection failed")

        val exception = shouldThrow<RetryableUpstreamServiceException> {
            executeUpstreamCall(
                serviceName = "Kodeverk",
                operation = "hent postnummer",
            ) { throw cause }
        }

        exception.cause shouldBe cause
        exception.statusCode shouldBe null
    }
}
