package no.nav.amt.lib.ktor.auth

import io.ktor.server.auth.jwt.JWTCredential

fun JWTCredential.erMaskinTilMaskin(): Boolean {
    val sub: String = payload.getClaim("sub").asString()
    val oid: String = payload.getClaim("oid").asString()
    return sub == oid
}
