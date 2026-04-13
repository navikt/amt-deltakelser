package no.nav.amt.distribusjon.application.plugins

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import no.nav.amt.distribusjon.Environment
import no.nav.amt.lib.ktor.auth.erMaskinTilMaskin
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.configureAuthentication(environment: Environment) {
    val jwkProvider = JwkProviderBuilder(URI(environment.azureJwkKeysUrl).toURL())
        .cached(5, 12, TimeUnit.HOURS)
        .build()

    install(Authentication) {
        jwt("SYSTEM") {
            verifier(jwkProvider, environment.azureJwtIssuer) {
                withAudience(environment.azureClientId)
            }

            validate { credentials ->
                fun reject(warning: String): Nothing? {
                    application.log.warn(warning)
                    return null
                }

                if (!credentials.erMaskinTilMaskin()) {
                    return@validate reject("Token med sluttbrukerkontekst har ikke tilgang til api med systemkontekst")
                }

                val azpClaim: String = credentials.payload.getClaim("azp").asString()
                val preAuthorizedApp = environment.preAuthorizedApps
                    .firstOrNull { it.clientId == azpClaim }

                if (preAuthorizedApp == null) {
                    return@validate reject("azp-claim $azpClaim matcher ingen applikasjoner i listen med preauthorized-apps")
                }

                JWTPrincipal(credentials.payload)
            }
        }
    }
}
