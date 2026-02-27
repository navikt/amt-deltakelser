package no.nav.amt.deltaker.bff.application.plugins

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.lib.ktor.auth.exceptions.AuthenticationException
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

const val ID_PORTEN_LEVEL4 = "Level4"
const val ID_PORTEN_LOA_HIGH = "idporten-loa-high"

enum class AuthLevel {
    INNBYGGER,
    VEILEDER,
    TILTAKSKOORDINATOR,
    SYSTEM,
}

fun Application.configureAuthentication(environment: Environment) {
    val azureJwkProvider = JwkProviderBuilder(URI(environment.azureJwkKeysUrl).toURL())
        .cached(5, 12, TimeUnit.HOURS)
        .build()

    val tokenXJwkProvider = JwkProviderBuilder(URI(environment.tokenXJwksUrl).toURL())
        .cached(5, 12, TimeUnit.HOURS)
        .build()

    fun JWTCredential.harRolle(rolle: UUID): Boolean {
        val navAnsattGroups = getListClaim("groups", UUID::class)
        return navAnsattGroups.contains(rolle)
    }

    val adRolleTiltakskoordinator = UUID.fromString(environment.adRolleTiltakskoordinator)

    install(Authentication) {
        jwt(AuthLevel.INNBYGGER.name) {
            verifier(tokenXJwkProvider, environment.tokenXJwtIssuer) {
                withAudience(environment.tokenXClientId)
            }
            validate {
                if (it["pid"] == null) {
                    application.log.warn("Ikke tilgang. Token mangler claim 'pid'.")
                    return@validate null
                }
                if (it["acr"] != ID_PORTEN_LEVEL4 && it["acr"] != ID_PORTEN_LOA_HIGH) {
                    application.log.warn("Ikke tilgang. Token mangler gyldig 'acr' claim.")
                    return@validate null
                }
                JWTPrincipal(it.payload)
            }
        }

        jwt(AuthLevel.VEILEDER.name) {
            verifier(azureJwkProvider, environment.azureJwtIssuer) {
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
        jwt(AuthLevel.TILTAKSKOORDINATOR.name) {
            verifier(azureJwkProvider, environment.azureJwtIssuer) {
                withAudience(environment.azureClientId)
            }

            validate { credentials ->
                credentials["NAVident"] ?: run {
                    application.log.warn("Ikke tilgang. Mangler claim 'NAVident'.")
                    return@validate null
                }

                if (!credentials.harRolle(adRolleTiltakskoordinator)) {
                    val ident = credentials["NAVident"]
                    application.log.warn("Ikke tilgang. $ident mangler rolle 0000-GA-TILTAK-DELTAKERE.")
                    return@validate null
                }

                JWTPrincipal(credentials.payload)
            }
        }

        jwt(AuthLevel.SYSTEM.name) {
            verifier(azureJwkProvider, environment.azureJwtIssuer) {
                withAudience(environment.azureClientId)
            }

            validate { credentials ->
                if (!erMaskinTilMaskin(credentials)) {
                    application.log.warn("Token med sluttbrukerkontekst har ikke tilgang til api med systemkontekst")
                    return@validate null
                }
                val appid: String = credentials.payload.getClaim("azp").asString()
                val app = environment.preAuthorizedApp.firstOrNull { it.clientId == appid }
                if (app?.appName !in listOf("tiltakspenger-tiltak")) {
                    application.log.warn("App-id $appid med navn ${app?.appName} har ikke tilgang til api med systemkontekst")
                    return@validate null
                }
                JWTPrincipal(credentials.payload)
            }
        }
    }
}

fun ApplicationCall.getNavAnsattAzureId(): UUID = this
    .principal<JWTPrincipal>()
    ?.get("oid")
    ?.let { UUID.fromString(it) }
    ?: throw AuthenticationException("NavAnsattAzureId mangler i JWTPrincipal")

fun ApplicationCall.getNavIdent(): String = this
    .principal<JWTPrincipal>()
    ?.get("NAVident")
    ?: throw AuthenticationException("NAVident mangler i JWTPrincipal")

fun ApplicationCall.getPersonIdent(): String = this
    .principal<JWTPrincipal>()
    ?.get("pid")
    ?: throw AuthenticationException("Pid mangler i JWTPrincipal")

fun erMaskinTilMaskin(credentials: JWTCredential): Boolean {
    val sub: String = credentials.payload.getClaim("sub").asString()
    val oid: String = credentials.payload.getClaim("oid").asString()
    return sub == oid
}
