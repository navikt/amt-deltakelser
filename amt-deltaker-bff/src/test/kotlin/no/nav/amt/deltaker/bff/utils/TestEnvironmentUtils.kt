package no.nav.amt.deltaker.bff.utils

import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.lib.ktor.auth.PreAuthorizedApp
import no.nav.amt.lib.utils.objectMapper
import java.lang.System.setProperty
import java.nio.file.Paths
import java.util.UUID

fun configureEnvForAuthentication() {
    val uri = Paths
        .get("src/test/resources/jwkset.json")
        .toUri()
        .toURL()
        .toString()

    val preAuthorizedApp = PreAuthorizedApp("dev:tpts:tiltakspenger-tiltak", "tiltakspenger-tiltak")

    setProperty(Environment.AZURE_OPENID_CONFIG_JWKS_URI_KEY, uri)
    setProperty(Environment.AZURE_OPENID_CONFIG_ISSUER_KEY, "issuer")
    setProperty(Environment.AZURE_APP_CLIENT_ID_KEY, "deltaker-bff")
    setProperty(Environment.TOKEN_X_JWKS_URI_KEY, uri)
    setProperty(Environment.TOKEN_X_ISSUER_KEY, "issuer")
    setProperty(Environment.TOKEN_X_CLIENT_ID_KEY, "deltaker-bff")
    setProperty(Environment.AD_ROLLE_TILTAKSKOORDINATOR, UUID(0L, 0L).toString())
    setProperty(Environment.AZURE_APP_PRE_AUTHORIZED_APPS, objectMapper.writeValueAsString(listOf(preAuthorizedApp)))
}
