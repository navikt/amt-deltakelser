package no.nav.amt.lib.spring.boot.client

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import java.io.IOException
import java.nio.charset.StandardCharsets

class ExternalServiceExceptionMapperTest {
    @Test
    fun `mapper nettverksfeil til retrybar exception`() {
        val exception = ResourceAccessException("boom", IOException("boom"))
            .toExternalServiceException("amt-arrangor", "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceRetryableException>()
        exception.message shouldBe "amt-arrangor: kunne ikke hente arrangør"
    }

    @Test
    fun `mapper clientfeil til ikke-retrybar exception`() {
        val exception = HttpClientErrorException
            .create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8,
            ).toExternalServiceException("amt-arrangor", "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceNonRetryableException>()
        exception.message shouldBe "amt-arrangor: kunne ikke hente arrangør. Status=404"
    }

    @Test
    fun `mapper 408 til retrybar exception`() {
        val exception = HttpClientErrorException
            .create(
                HttpStatus.REQUEST_TIMEOUT,
                "Request Timeout",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8,
            ).toExternalServiceException("amt-arrangor", "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceRetryableException>()
        exception.message shouldBe "amt-arrangor: kunne ikke hente arrangør. Status=408"
    }

    @Test
    fun `mapper 429 til retrybar exception`() {
        val exception = HttpClientErrorException
            .create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8,
            ).toExternalServiceException("amt-arrangor", "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceRetryableException>()
        exception.message shouldBe "amt-arrangor: kunne ikke hente arrangør. Status=429"
    }

    @Test
    fun `mapper 501 til ikke-retrybar exception`() {
        val exception = HttpServerErrorException
            .create(
                HttpStatus.NOT_IMPLEMENTED,
                "Not Implemented",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8,
            ).toExternalServiceException("amt-arrangor", "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceNonRetryableException>()
        exception.message shouldBe "amt-arrangor: kunne ikke hente arrangør. Status=501"
    }

    @Test
    fun `mapper 505 til ikke-retrybar exception`() {
        val exception = HttpServerErrorException
            .create(
                HttpStatus.HTTP_VERSION_NOT_SUPPORTED,
                "HTTP Version Not Supported",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8,
            ).toExternalServiceException("amt-arrangor", "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceNonRetryableException>()
        exception.message shouldBe "amt-arrangor: kunne ikke hente arrangør. Status=505"
    }

    @Test
    fun `bruker unauthorized callback ved 403`() {
        val exception = HttpClientErrorException
            .create(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8,
            ).toExternalServiceException("amt-arrangor", "hente arrangør") { serviceName, action ->
                IllegalArgumentException("$serviceName custom $action")
            }

        exception.shouldBeInstanceOf<IllegalArgumentException>()
        exception.message shouldBe "amt-arrangor custom hente arrangør"
        exception.cause.shouldBeNull()
    }

    @Test
    fun `mapper andre restclientfeil til ikke-retrybar exception`() {
        val exception = RestClientException("boom")
            .toExternalServiceException("amt-arrangor", "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceNonRetryableException>()
        exception.message shouldBe "amt-arrangor: kunne ikke hente arrangør"
    }
}
