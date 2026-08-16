package no.nav.amt.aktivitetskort.client

import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.lib.spring.boot.client.ExternalServiceNonRetryableException
import no.nav.amt.lib.spring.boot.client.ExternalServiceRetryableException
import no.nav.amt.lib.spring.boot.client.toExternalServiceException
import no.nav.amt.person.service.clients.AMT_ARRANGOR_CLIENT_ID
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import java.io.IOException
import java.nio.charset.StandardCharsets

class ClientExceptionMapperTest {
    @Test
    fun `mapperer nettverksfeil til retrybar exception`() {
        val exception = ResourceAccessException("boom", IOException("boom"))
            .toExternalServiceException(AMT_ARRANGOR_CLIENT_ID, "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceRetryableException>()
    }

    @Test
    fun `mapperer clientfeil til ikke-retrybar exception`() {
        val exception = HttpClientErrorException
            .create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8,
            ).toExternalServiceException(AMT_ARRANGOR_CLIENT_ID, "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceNonRetryableException>()
    }

    @Test
    fun `mapperer unauthorized til ikke-retrybar exception`() {
        val exception = HttpClientErrorException
            .create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8,
            ).toExternalServiceException(AMT_ARRANGOR_CLIENT_ID, "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceNonRetryableException>()
    }

    @Test
    fun `mapperer forbidden til ikke-retrybar exception`() {
        val exception = HttpClientErrorException
            .create(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8,
            ).toExternalServiceException(AMT_ARRANGOR_CLIENT_ID, "hente arrangør")

        exception.shouldBeInstanceOf<ExternalServiceNonRetryableException>()
    }
}
