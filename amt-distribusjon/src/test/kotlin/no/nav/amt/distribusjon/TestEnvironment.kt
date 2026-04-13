package no.nav.amt.distribusjon

import no.nav.amt.lib.ktor.auth.PreAuthorizedApp
import java.nio.file.Paths

val preAuthorizedApps = listOf(
    PreAuthorizedApp(
        name = "dev:amt:amt-deltaker-bff",
        clientId = "amt-deltaker-bff",
    ),
)

val testEnvironment = Environment(
    dokdistkanalScope = "dokdistkanal.scope",
    dokdistkanalUrl = "http://dokdistkanal",
    veilarboppfolgingUrl = "http://veilarboppfolging",
    veilarboppfolgingScope = "veilarboppfolging.scope",
    amtPersonScope = "amt-person.scope",
    amtPersonUrl = "http://amt-person",
    azureClientId = "amt-distribusjon",
    azureJwtIssuer = "issuer",
    azureJwkKeysUrl = getAzureJwkKeysUrl(),
    preAuthorizedApps = preAuthorizedApps,
)

fun getAzureJwkKeysUrl(): String {
    val path = "src/test/resources/jwkset.json"
    return Paths
        .get(path)
        .toUri()
        .toURL()
        .toString()
}
