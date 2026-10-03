package no.nav.tiltaksarrangor.api

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler(includeStacktrace = false)
    private val request = MockHttpServletRequest("GET", "/")

    @Test
    fun `retryable upstream error becomes 503`() {
        val exception = RetryableUpstreamServiceException(
            serviceName = "upstream",
            operation = "read",
            statusCode = null,
            cause = ResourceAccessException("connection failed"),
        )

        handler.handleException(exception, request).statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
    }

    @Test
    fun `non-retryable upstream error becomes 502`() {
        val exception = UpstreamServiceException(
            serviceName = "upstream",
            operation = "read",
            statusCode = 400,
            cause = HttpClientErrorException(HttpStatus.BAD_REQUEST),
        )

        handler.handleException(exception, request).statusCode shouldBe HttpStatus.BAD_GATEWAY
    }
}
