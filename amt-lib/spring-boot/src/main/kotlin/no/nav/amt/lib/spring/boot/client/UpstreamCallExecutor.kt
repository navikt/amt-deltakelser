package no.nav.amt.lib.spring.boot.client

import no.nav.amt.lib.spring.boot.client.exception.RetryableUpstreamServiceException
import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
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
 * klassifisert som ikke-midlertidige. Andre exception-typer propageres uendret.
 * 401- og 403-svar logges på error-nivå som standard. Sett
 * `logAuthorizationFailures = false` når slike avslag kan være forventet,
 * for eksempel ved kall med brukerkontekst.
 * Bruk bare stabile verdier uten personopplysninger for `serviceName` og
 * `operation`, siden verdiene skrives til loggen ved avvisning.
 * Retry håndteres av kallestedet og utføres ikke av denne funksjonen.
 *
 * @param serviceName navnet på den eksterne tjenesten
 * @param operation operasjonen som utføres
 * @param logAuthorizationFailures om 401- og 403-svar skal logges, standard `true`
 * @param call HTTP-kallet som skal utføres
 * @return resultatet fra [call]
 * @throws RetryableUpstreamServiceException ved midlertidige HTTP- eller nettverksfeil
 * @throws UpstreamServiceException ved andre feil fra Spring HTTP-klienten
 */
@JvmOverloads
fun <T> executeUpstreamCall(
    serviceName: String,
    operation: String,
    logAuthorizationFailures: Boolean = true,
    call: () -> T,
): T = try {
    call()
} catch (e: RestClientException) {
    val statusCode = when (e) {
        is RestClientResponseException -> e.statusCode.value()
        else -> null
    }

    if (logAuthorizationFailures && (statusCode == 401 || statusCode == 403)) {
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

/**
 * Utfører et upstream-kall som må returnere en response body.
 *
 * Bruk denne for HTTP-klienter der et tomt svar ikke er gyldig. Kallet må returnere en
 * [ResponseEntity] slik at statuskoden bevares også når body mangler. Kall som tillater
 * tom body, skal bruke [executeUpstreamCall].
 *
 * @param serviceName navnet på den eksterne tjenesten
 * @param operation operasjonen som utføres
 * @param logAuthorizationFailures om 401- og 403-svar skal logges, standard `true`
 * @param call HTTP-kallet som skal utføres
 * @return response body
 * @throws RetryableUpstreamServiceException ved midlertidige HTTP- eller nettverksfeil
 * @throws UpstreamServiceException ved andre feil fra Spring HTTP-klienten eller manglende body
 */
fun <T : Any> executeUpstreamCallWithRequiredBody(
    serviceName: String,
    operation: String,
    logAuthorizationFailures: Boolean = true,
    call: () -> ResponseEntity<T>,
): T {
    val response = executeUpstreamCall(
        serviceName = serviceName,
        operation = operation,
        logAuthorizationFailures = logAuthorizationFailures,
        call = call,
    )

    return response.body ?: throw UpstreamServiceException(
        serviceName = serviceName,
        operation = operation,
        statusCode = response.statusCode.value(),
        cause = RestClientException("Required upstream response body was empty"),
    )
}

private val retryableHttpStatusCodes = setOf(408, 429, 500, 502, 503, 504)
