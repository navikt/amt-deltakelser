package no.nav.tiltaksarrangor.api

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.servlet.http.HttpServletRequest
import no.nav.amt.lib.spring.boot.client.ExternalServiceNonRetryableException
import no.nav.amt.lib.spring.boot.client.ExternalServiceRetryableException
import no.nav.tiltaksarrangor.model.exceptions.SkjultDeltakerException
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.model.exceptions.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler(
    @Value($$"${rest.include-stacktrace}") private val includeStacktrace: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(Exception::class)
    fun handleException(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<Response> = when (ex) {
        is ValidationException -> buildResponse(HttpStatus.BAD_REQUEST, ex)
        is SkjultDeltakerException -> buildResponse(HttpStatus.BAD_REQUEST, ex)
        is AuthenticationException -> buildResponse(HttpStatus.UNAUTHORIZED, ex)
        is AccessDeniedException -> buildResponse(HttpStatus.FORBIDDEN, ex)
        is UnauthorizedException -> buildResponse(HttpStatus.FORBIDDEN, ex)
        is ExternalServiceNonRetryableException -> buildResponse(HttpStatus.BAD_GATEWAY, ex)
        is ExternalServiceRetryableException -> buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex)
        is NoSuchElementException -> buildResponse(HttpStatus.NOT_FOUND, ex)
        is IllegalStateException -> buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex)
        else -> {
            log.error("Internal server error - ${ex.message} - ${request.method}: ${request.requestURI}", ex)
            buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex)
        }
    }

    private fun buildResponse(
        status: HttpStatus,
        exception: Throwable,
    ): ResponseEntity<Response> {
        if (status.is4xxClientError) {
            log.warn("Noe er feil med request: ${exception.message}, statuskode ${status.value()}", exception)
        } else {
            log.error("Noe gikk galt: ${exception.message}, statuskode ${status.value()}", exception)
        }
        return ResponseEntity
            .status(status)
            .body(
                Response(
                    status = status.value(),
                    title = status,
                    stacktrace = if (includeStacktrace) exception.stackTraceToString() else null,
                ),
            )
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Response(
        val status: Int,
        val title: HttpStatus,
        val stacktrace: String? = null,
    )
}
