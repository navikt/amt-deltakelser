package no.nav.amt.distribusjon

import java.nio.file.Paths

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
)

fun getAzureJwkKeysUrl(): String {
    val path = "src/test/resources/jwkset.json"
    return Paths
        .get(path)
        .toUri()
        .toURL()
        .toString()
}
