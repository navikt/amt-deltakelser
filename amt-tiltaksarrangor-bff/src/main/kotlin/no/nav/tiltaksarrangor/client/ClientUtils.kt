package no.nav.tiltaksarrangor.client

import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse

object ClientUtils {
    fun handleClientError(
        log: org.slf4j.Logger,
        unauthorizedMessage: String,
        defaultErrorMessage: String,
        notFoundMessage: String? = null,
    ): (HttpRequest, ClientHttpResponse) -> Unit = { _, response ->
        when (response.statusCode) {
            HttpStatus.NOT_FOUND -> {
                val message = notFoundMessage ?: "Ressurs ikke funnet"
                log.info(message)
                throw NoSuchElementException(message)
            }

            HttpStatus.UNAUTHORIZED,
            HttpStatus.FORBIDDEN,
            -> throw UnauthorizedException(unauthorizedMessage)

            else -> {
                log.error("$defaultErrorMessage Responsekode: ${response.statusCode.value()}")
                throw RuntimeException(defaultErrorMessage)
            }
        }
    }
}
