package no.nav.tiltaksarrangor.client

import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.lib.spring.boot.client.executeUpstreamCall
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException

fun <T> executeUpstreamCallWithUnauthorizedMapping(
    serviceName: String,
    operation: String,
    unauthorizedMessage: String,
    call: () -> T,
): T = try {
    executeUpstreamCall(serviceName, operation, call)
} catch (e: UpstreamServiceException) {
    if (e.statusCode == 401 || e.statusCode == 403) {
        throw UnauthorizedException(unauthorizedMessage)
    }
    throw e
}
