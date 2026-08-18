package no.nav.amt.lib.spring.boot.client

open class ExternalServiceException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

class ExternalServiceRetryableException(
    message: String,
    cause: Throwable,
) : ExternalServiceException(message, cause)

class ExternalServiceNonRetryableException(
    message: String,
    cause: Throwable,
) : ExternalServiceException(message, cause)
