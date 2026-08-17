package no.nav.tiltaksarrangor.client

import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import org.springframework.web.client.RestClientException
import no.nav.amt.lib.spring.boot.client.toExternalServiceException as toSharedExternalServiceException

internal fun RestClientException.toExternalServiceException(
    serviceName: String,
    action: String,
    unauthorizedMessage: String,
): RuntimeException = toSharedExternalServiceException(serviceName, action) { _, _ -> UnauthorizedException(unauthorizedMessage) }
