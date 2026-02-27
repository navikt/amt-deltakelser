package no.nav.amt.deltaker.application.plugins

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTAuthenticationProvider
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.ktor.auth.exceptions.AuthenticationException
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

fun Application.configureAuthentication(environment: Environment) {
    val jwkProvider = JwkProviderBuilder(URI(environment.jwkKeysUrl).toURL())
        .cached(5, 12, TimeUnit.HOURS)
        .build()

    install(Authentication) {
        jwt("SYSTEM") {
            validerPreAuthorizedApps(jwkProvider, setOf("amt-deltaker-bff", "amt-distribusjon", "amt-tiltaksarrangor-bff"), environment)
        }

        jwt("EXTERNAL-SYSTEM") {
            validerPreAuthorizedApps(jwkProvider, setOf("veilarboppfolging"), environment)
        }

        jwt("MULIGHETSROMMET-SYSTEM") {
            validerPreAuthorizedApps(jwkProvider, setOf("mulighetsrommet-api"), environment)
        }

        jwt("VEILEDER") {
            verifier(jwkProvider, environment.jwtIssuer) {
                withAudience(environment.azureClientId)
            }

            validate { credentials ->
                credentials["NAVident"] ?: run {
                    application.log.warn("Ikke tilgang. Mangler claim 'NAVident'.")
                    return@validate null
                }
                JWTPrincipal(credentials.payload)
            }
        }
    }
}

private fun JWTAuthenticationProvider.Config.validerPreAuthorizedApps(
    jwkProvider: JwkProvider,
    apperMedTilgang: Set<String>,
    environment: Environment,
) {
    verifier(jwkProvider, environment.jwtIssuer) {
        withAudience(environment.azureClientId)
    }
    validate { credentials ->
        fun reject(warning: String): Nothing? {
            application.log.warn(warning)
            return null
        }

        if (!erMaskinTilMaskin(credentials)) {
            return@validate reject("Token med sluttbrukerkontekst har ikke tilgang til api med systemkontekst")
        }

        val azpClaim: String = credentials.payload.getClaim("azp").asString()
        val preAuthorizedApp = environment.preAuthorizedApps
            .firstOrNull { it.clientId == azpClaim }

        if (preAuthorizedApp == null) {
            return@validate reject("azp-claim $azpClaim matcher ingen applikasjoner i listen med preauthorized-apps")
        }

        if (preAuthorizedApp.appName !in apperMedTilgang) {
            return@validate reject("App-id $azpClaim med navn ${preAuthorizedApp.appName} har ikke tilgang til api med systemkontekst")
        }

        JWTPrincipal(credentials.payload)
    }
}

fun erMaskinTilMaskin(credentials: JWTCredential): Boolean {
    val sub: String = credentials.payload.getClaim("sub").asString()
    val oid: String = credentials.payload.getClaim("oid").asString()
    return sub == oid
}

fun ApplicationCall.getNavAnsattAzureId(): UUID = this
    .principal<JWTPrincipal>()
    ?.get("oid")
    ?.let { UUID.fromString(it) }
    ?: throw AuthenticationException("NavAnsattAzureId mangler i JWTPrincipal")
