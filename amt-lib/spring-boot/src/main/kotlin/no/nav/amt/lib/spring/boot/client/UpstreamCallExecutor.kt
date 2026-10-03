package no.nav.amt.lib.spring.boot.client

import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import org.slf4j.LoggerFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

private val log = LoggerFactory.getLogger("no.nav.amt.lib.spring.boot.client.UpstreamCallExecutor")

/**
 * Utfører et kall med Spring HTTP-klienten og oversetter klientfeil til
 * [UpstreamServiceException]-typer.
 *
 * Nettverksfeil og HTTP-statusene 408, 429, 500, 502, 503 og 504 blir
 * klassifisert som midlertidige. Andre `RestClientException`-feil blir
 * klassifisert som ikke-midlertidige. 401- og 403-svar logges med tjeneste,
 * operasjon og statuskode. Andre exception-typer propageres uendret.
 * Retry håndteres av kallestedet og utføres ikke av denne funksjonen.
 *
 * @param serviceName navnet på den eksterne tjenesten
 * @param operation operasjonen som utføres
 * @param call HTTP-kallet som skal utføres
 * @return resultatet fra [call]
 * @throws RetryableUpstreamServiceException ved midlertidige HTTP- eller nettverksfeil
 * @throws UpstreamServiceException ved andre feil fra Spring HTTP-klienten
 */
fun <T> executeUpstreamCall(
    serviceName: String,
    operation: String,
    call: () -> T,
): T = try {
    call()
} catch (e: RestClientException) {
    val statusCode = when (e) {
        is RestClientResponseException -> e.statusCode.value()
        else -> null
    }

    if (statusCode == 401 || statusCode == 403) {
        log.error(
            "Kall mot ekstern tjeneste ble avvist (serviceName={}, operation={}, statusCode={})",
            serviceName,
            operation,
            statusCode,
        )
    }

    val isTransient = when {
        e is ResourceAccessException -> true
        statusCode != null -> statusCode in retryableHttpStatusCodes
        else -> false
    }

    if (isTransient) {
        throw RetryableUpstreamServiceException(
            serviceName = serviceName,
            operation = operation,
            statusCode = statusCode,
            cause = e,
        )
    }

    throw UpstreamServiceException(
        serviceName = serviceName,
        operation = operation,
        statusCode = statusCode,
        cause = e,
    )
}

private val retryableHttpStatusCodes = setOf(408, 429, 500, 502, 503, 504)
