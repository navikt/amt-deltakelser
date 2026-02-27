package no.nav.amt.deltaker.bff.apiclients

import io.ktor.http.HttpStatusCode
import no.nav.amt.lib.ktor.auth.exceptions.AuthenticationException
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException

@Suppress("unused")
object ApiClientTestUtils {
    @JvmStatic
    fun failureCases() = listOf(
        Pair(HttpStatusCode.Unauthorized, AuthenticationException::class),
        Pair(HttpStatusCode.Forbidden, AuthorizationException::class),
        Pair(HttpStatusCode.BadRequest, IllegalArgumentException::class),
        Pair(HttpStatusCode.NotFound, NoSuchElementException::class),
        Pair(HttpStatusCode.InternalServerError, IllegalStateException::class),
    )
}
