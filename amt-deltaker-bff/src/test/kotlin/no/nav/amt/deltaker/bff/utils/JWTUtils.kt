package no.nav.amt.deltaker.bff.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.application.plugins.ID_PORTEN_LOA_HIGH
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.text.ParseException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID

const val KEY_ID = "localhost-signer"

/* Utsteder en Bearer-token (En slik vi ber AzureAd om). OBS: Det er viktig at KeyId matcher kid i jwkset.json
 */
fun generateJWT(
    consumerClientId: String,
    navAnsattAzureId: String,
    audience: String,
    navIdent: String = "Z123456",
    expiry: LocalDateTime? = LocalDateTime.now().plusHours(1),
    subject: String = "subject",
    issuer: String = "issuer",
    groups: List<String> = emptyList(),
): String {
    val now = Date()
    val key = getDefaultRSAKey()
    val alg = Algorithm.RSA256(key.toRSAPublicKey(), key.toRSAPrivateKey())

    return JWT
        .create()
        .withKeyId(KEY_ID)
        .withSubject(subject)
        .withIssuer(issuer)
        .withAudience(audience)
        .withJWTId(UUID.randomUUID().toString())
        .withClaim("ver", "1.0")
        .withClaim("nonce", "myNonce")
        .withClaim("auth_time", now)
        .withClaim("nbf", now)
        .withClaim("azp", consumerClientId)
        .withClaim("oid", navAnsattAzureId)
        .withClaim("NAVident", navIdent)
        .withClaim("iat", now)
        .withClaim("exp", Date.from(expiry?.atZone(ZoneId.systemDefault())?.toInstant()))
        .withClaim("groups", groups)
        .sign(alg)
}

fun generateSystemJWT(
    consumerClientId: String,
    audience: String,
    expiry: LocalDateTime? = LocalDateTime.now().plusHours(1),
    subject: String = "subject",
    issuer: String = "issuer",
    groups: List<String> = emptyList(),
): String {
    val now = Date()
    val key = getDefaultRSAKey()
    val alg = Algorithm.RSA256(key.toRSAPublicKey(), key.toRSAPrivateKey())

    return JWT
        .create()
        .withKeyId(KEY_ID)
        .withSubject(subject)
        .withIssuer(issuer)
        .withAudience(audience)
        .withJWTId(UUID.randomUUID().toString())
        .withClaim("ver", "1.0")
        .withClaim("nonce", "myNonce")
        .withClaim("auth_time", now)
        .withClaim("nbf", now)
        .withClaim("azp", consumerClientId)
        .withClaim("oid", subject)
        .withClaim("iat", now)
        .withClaim("exp", Date.from(expiry?.atZone(ZoneId.systemDefault())?.toInstant()))
        .withClaim("groups", groups)
        .sign(alg)
}

fun tokenXToken(pid: String, env: Environment): String? {
    val key = getDefaultRSAKey()
    return JWT
        .create()
        .withAudience(env.tokenXClientId)
        .withIssuer(env.tokenXJwtIssuer)
        .withClaim("pid", pid)
        .withClaim("acr", ID_PORTEN_LOA_HIGH)
        .withExpiresAt(Date(System.currentTimeMillis() + 60000))
        .sign(Algorithm.RSA256(key.toRSAPublicKey(), key.toRSAPrivateKey()))
}

private fun getDefaultRSAKey(): RSAKey = getJWKSet().getKeyByKeyId(KEY_ID) as RSAKey

private fun getJWKSet(): JWKSet {
    try {
        return JWKSet.parse(getFileAsString("src/test/resources/jwkset.json"))
    } catch (io: IOException) {
        throw RuntimeException(io)
    } catch (io: ParseException) {
        throw RuntimeException(io)
    }
}

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
