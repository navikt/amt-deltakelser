package no.nav.amt.lib.spring.boot.client.exception

import org.springframework.web.client.RestClientException

/**
 * Midlertidig feil ved kall mot en ekstern tjeneste.
 *
 * Typen lar kallestedet velge om feilen skal forsøkes på nytt; exceptionen
 * utfører ikke retry på egen hånd.
 *
 * @param serviceName navnet på den eksterne tjenesten
 * @param operation operasjonen som feilet
 * @param statusCode HTTP-statuskode, eller `null` hvis ingen HTTP-respons kom fram
 * @param cause den opprinnelige feilen fra Spring HTTP-klienten
 */
class RetryableUpstreamServiceException(
    serviceName: String,
    operation: String,
    statusCode: Int?,
    cause: RestClientException,
) : UpstreamServiceException(
        serviceName = serviceName,
        operation = operation,
        statusCode = statusCode,
        cause = cause,
    )
