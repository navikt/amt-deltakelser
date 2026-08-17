package no.nav.amt.lib.spring.boot.client

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

fun RestClientException.toExternalServiceException(
    serviceName: String,
    action: String,
    onUnauthorized: ((serviceName: String, action: String) -> RuntimeException)? = null,
): RuntimeException = when (this) {
    is RestClientResponseException -> toExternalServiceResponseException(
        serviceName = serviceName,
        action = action,
        onUnauthorized = onUnauthorized,
    )

    is ResourceAccessException -> ExternalServiceRetryableException(
        errorMessage(
            serviceName = serviceName,
            action = action,
        ),
        this,
    )

    else -> ExternalServiceNonRetryableException(
        errorMessage(
            serviceName = serviceName,
            action = action,
        ),
        this,
    )
}

private fun RestClientResponseException.toExternalServiceResponseException(
    serviceName: String,
    action: String,
    onUnauthorized: ((serviceName: String, action: String) -> RuntimeException)?,
): RuntimeException {
    val message = errorMessage(serviceName, action, statusCode.value())

    return when {
        statusCode == HttpStatus.UNAUTHORIZED || statusCode == HttpStatus.FORBIDDEN ->
            onUnauthorized?.invoke(
                serviceName,
                action,
            ) ?: ExternalServiceNonRetryableException(message, this)

        statusCode.isNonRetryableExternalServiceError() ->
            ExternalServiceNonRetryableException(message, this)

        else -> ExternalServiceRetryableException(message, this)
    }
}

private fun errorMessage(
    serviceName: String,
    action: String,
    status: Int? = null,
): String = status?.let { "$serviceName: kunne ikke $action. Status=$it" } ?: "$serviceName: kunne ikke $action"

private fun HttpStatusCode.isNonRetryableExternalServiceError(): Boolean = (
    is4xxClientError &&
        this != HttpStatus.REQUEST_TIMEOUT &&
        this != HttpStatus.TOO_MANY_REQUESTS
) ||
    this == HttpStatus.NOT_IMPLEMENTED ||
    this == HttpStatus.HTTP_VERSION_NOT_SUPPORTED
