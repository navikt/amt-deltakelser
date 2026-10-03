package no.nav.amt.lib.spring.boot.client

@Deprecated("Use UpstreamServiceException or RetryableUpstreamServiceException instead.")
open class ExternalServiceException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

@Deprecated("Use RetryableUpstreamServiceException instead.")
@Suppress("DEPRECATION")
class ExternalServiceRetryableException(
    message: String,
    cause: Throwable,
) : ExternalServiceException(message, cause)

@Deprecated("Use UpstreamServiceException instead.")
@Suppress("DEPRECATION")
class ExternalServiceNonRetryableException(
    message: String,
    cause: Throwable,
) : ExternalServiceException(message, cause)
