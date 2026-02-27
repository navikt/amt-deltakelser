package no.nav.amt.deltaker.utils

import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.ktor.auth.PreAuthorizedApp
import no.nav.amt.lib.utils.objectMapper
import java.lang.System.setProperty
import java.nio.file.Paths

fun configureEnvForAuthentication() {
    val uri = Paths
        .get("src/test/resources/jwkset.json")
        .toUri()
        .toURL()
        .toString()

    val preAuthorizedApps = listOf(
        PreAuthorizedApp("dev:amt:amt-deltaker-bff", "amt-deltaker-bff"),
        PreAuthorizedApp("dev:mulighetsrommet:mulighetsrommet-api", "mulighetsrommet-api"),
    )

    setProperty(Environment.AZURE_OPENID_CONFIG_JWKS_URI_KEY, uri)
    setProperty(Environment.AZURE_OPENID_CONFIG_ISSUER_KEY, "issuer")
    setProperty(Environment.AZURE_APP_CLIENT_ID_KEY, "amt-deltaker")
    setProperty(Environment.AZURE_APP_PRE_AUTHORIZED_APPS, objectMapper.writeValueAsString(preAuthorizedApps))
}
