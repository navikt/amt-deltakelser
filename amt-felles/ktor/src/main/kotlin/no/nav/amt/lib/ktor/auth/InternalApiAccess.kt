package no.nav.amt.lib.ktor.auth

import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException

fun requireInternal(remoteAddress: String) {
    if (!isInternal(remoteAddress)) throw AuthorizationException("Ikke tilgang til api")
}

fun isInternal(remoteAddress: String): Boolean = remoteAddress in setOf(
    "127.0.0.1",
    "::1",
    "0:0:0:0:0:0:0:1",
    "localhost",
)
