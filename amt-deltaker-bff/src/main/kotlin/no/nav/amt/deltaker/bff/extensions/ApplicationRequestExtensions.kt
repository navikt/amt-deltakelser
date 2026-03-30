package no.nav.amt.deltaker.bff.extensions

import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header

fun ApplicationRequest.headerNotNull(navn: String): String {
    call.request.header(navn)?.let { return it }

    throw IllegalArgumentException("Påkrevd header: $navn er null")
}
