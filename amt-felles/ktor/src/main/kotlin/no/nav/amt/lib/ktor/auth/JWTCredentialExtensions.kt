package no.nav.amt.lib.ktor.auth

import io.ktor.server.auth.jwt.JWTCredential

internal const val SUB_CLAIM = "sub"
internal const val OID_CLAIM = "oid"

fun JWTCredential.erMaskinTilMaskin(): Boolean {
    val sub = payload.getClaim(SUB_CLAIM).asString() ?: return false
    val oid = payload.getClaim(OID_CLAIM).asString() ?: return false
    return sub == oid
}
