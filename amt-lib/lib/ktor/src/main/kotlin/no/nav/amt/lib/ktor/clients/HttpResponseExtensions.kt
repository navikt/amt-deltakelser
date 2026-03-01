package no.nav.amt.lib.ktor.clients

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import no.nav.amt.lib.ktor.auth.exceptions.AuthenticationException
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException

suspend fun HttpResponse.failIfNotSuccess(errorDescription: String): HttpResponse = if (!status.isSuccess()) {
    val bodyAsText = runCatching { bodyAsText() }.getOrElse { "Kunne ikke lese respons" }
    val fullErrorDescription = "$errorDescription Status=${this.status.value} error=$bodyAsText"

    when (status) {
        HttpStatusCode.Unauthorized -> throw AuthenticationException(fullErrorDescription)
        HttpStatusCode.Forbidden -> throw AuthorizationException(fullErrorDescription)
        HttpStatusCode.BadRequest -> throw IllegalArgumentException(fullErrorDescription)
        HttpStatusCode.NotFound -> throw NoSuchElementException(fullErrorDescription)
        else -> throw IllegalStateException(fullErrorDescription) // gir HttpStatusCode.InternalServerError i StatusPages
    }
} else {
    this
}
