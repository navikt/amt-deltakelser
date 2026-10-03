package no.nav.amt.lib.spring.boot.client.exception

import org.springframework.web.client.RestClientException

/**
 * Felles exception-type for feil ved kall mot en ekstern tjeneste, både midlertidige og ikke-midlertidige.
 *
 * Feilmeldingen inneholder tjeneste, operasjon og eventuell HTTP-status, men
 * ikke den rå feilmeldingen fra tjenesten.
 *
 * @property serviceName navnet på den eksterne tjenesten
 * @property operation operasjonen som feilet
 * @property statusCode HTTP-statuskode, eller `null` hvis ingen HTTP-respons kom fram
 * @param cause den opprinnelige feilen fra Spring HTTP-klienten
 */
open class UpstreamServiceException(
    val serviceName: String,
    val operation: String,
    val statusCode: Int?,
    cause: RestClientException,
) : RuntimeException(
        message(
            serviceName = serviceName,
            operation = operation,
            statusCode = statusCode,
        ),
        cause,
    ) {
    companion object {
        private fun message(
            serviceName: String,
            operation: String,
            statusCode: Int?,
        ): String = buildString {
            append("Kall mot $serviceName feilet under $operation")
            statusCode?.let { append(" (HTTP $it)") }
        }
    }
}
