package tjenester.auth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.oauth2.sdk.TokenRequest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import no.nav.security.mock.oauth2.extensions.clientIdAsString
import no.nav.security.mock.oauth2.extensions.scopesWithoutOidcScopes
import no.nav.security.mock.oauth2.token.OAuth2TokenCallback

const val MOCK_OAUTH2_PORT = 9000

fun startMockOAuth2Server(): MockOAuth2Server {
    val azureMock = SimNavAzureTokenCallback()
    val tokenXMock = TokenXTokenCallback()

    return MockOAuth2Server(
        OAuth2Config(
            interactiveLogin = true,
            tokenCallbacks = setOf(azureMock, tokenXMock),
        ),
    ).also {
        it.start(MOCK_OAUTH2_PORT)
    }
}

private class TokenXTokenCallback : OAuth2TokenCallback {
    override fun issuerId(): String = "tokenx"

    override fun subject(tokenRequest: TokenRequest): String? = claimsFor(tokenRequest)["sub"] as? String

    override fun typeHeader(tokenRequest: TokenRequest): String = JOSEObjectType.JWT.type

    override fun audience(tokenRequest: TokenRequest): List<String> =
        when (val audienceClaim = claimsFor(tokenRequest)["aud"]) {
            is List<*> -> audienceClaim.filterIsInstance<String>()
            else -> emptyList()
        }

    override fun addClaims(tokenRequest: TokenRequest): Map<String, Any> = claimsFor(tokenRequest)

    override fun tokenExpiry(): Long = 315360000

    private fun claimsFor(tokenRequest: TokenRequest): Map<String, Any> {
        val clientId = tokenRequest.clientIdAsString()
        val audience = tokenRequest.getCustomParameter("audience").orEmpty().firstOrNull()

        return when {
            clientId == "tiltaksarrangor-flate" -> mapOf(
                "pid" to "01019050188",
                "aud" to listOf("amt-tiltaksarrangor-bff"),
                "sub" to "11111111-1111-1111-1111-111111111111",
            )

            clientId == "amt-tiltaksarrangor-bff" -> mapOf(
                "sub" to "11111111-1111-1111-1111-111111111111",
                "aud" to listOf("amt-arrangor"),
            )

            audience == "amt-arrangor" -> mapOf(
                "sub" to "11111111-1111-1111-1111-111111111111",
                "aud" to listOf("amt-arrangor"),
            )

            clientId == "innbyggers-flate" -> {
                val pidOrBlank = FrontendAuthState.getPid() ?: ""
                mapOf(
                    "oid" to "11111111-1111-1111-1111-111111111111",
                    "groups" to emptyList<String>(),
                    "aud" to listOf("innbygger-client-id"),
                    "acr" to "Level4",
                    "pid" to pidOrBlank,
                )
            }

            else -> emptyMap()
        }
    }
}

private class SimNavAzureTokenCallback : OAuth2TokenCallback {
    override fun issuerId(): String = "azure"

    override fun subject(tokenRequest: TokenRequest): String? = claimsFor(tokenRequest)["sub"] as? String

    override fun typeHeader(tokenRequest: TokenRequest): String = JOSEObjectType.JWT.type

    override fun audience(tokenRequest: TokenRequest): List<String> =
        when (val audienceClaim = claimsFor(tokenRequest)["aud"]) {
            is List<*> -> audienceClaim.filterIsInstance<String>()
            else -> emptyList()
        }

    override fun addClaims(tokenRequest: TokenRequest): Map<String, Any> = claimsFor(tokenRequest)

    override fun tokenExpiry(): Long = 315360000

    private fun claimsFor(tokenRequest: TokenRequest): Map<String, Any> {
        val clientId = tokenRequest.clientIdAsString()
        val scopes = tokenRequest.scopesWithoutOidcScopes()

        val navIdentOrEmpty = FrontendAuthState.getNavIdent() ?: ""

        return when {
            clientId == "amt-deltaker-bff" -> mapOf(
                "NAVident" to navIdentOrEmpty,
                "oid" to "11111111-1111-1111-1111-111111111111",
                "sub" to "11111111-1111-1111-1111-111111111111",
                "azp" to "amt-deltaker-bff",
                "groups" to emptyList<String>(),
                "aud" to listOf("amt-deltaker", "amt-arrangor", "amt-person-service", "amt-distribusjon"),
            )

            clientId == "nav-veileders-flate" -> {
                mapOf(
                    "NAVident" to navIdentOrEmpty,
                    "oid" to "11111111-1111-1111-1111-111111111111",
                    "groups" to emptyList<String>(),
                    "aud" to listOf("amt-deltaker-bff"),
                )
            }

            clientId == "tiltakskoordinator-flate" -> {
                mapOf(
                    "NAVident" to navIdentOrEmpty,
                    "oid" to "11111111-1111-1111-1111-111111111111",
                    "groups" to listOf(
                        // AD_ROLLE_TILTAKSKOORDINATOR (se konfigurasjon for amt-deltaker-bff)
                        "c13484a2-3994-4653-9f57-5082c352e656"
                    ),
                    "aud" to listOf("amt-deltaker-bff"),
                )
            }


            "api://amt-distribusjon/.default" in scopes -> mapOf(
                "aud" to listOf("amt-distribusjon"),
                "sub" to "11111111-1111-1111-1111-111111111111",
                "oid" to "11111111-1111-1111-1111-111111111111",
                "azp" to "amt-deltaker",
            )

            "api://amt-enhetsregister/.default" in scopes -> mapOf(
                "aud" to listOf("amt-enhetsregister"),
                "sub" to "11111111-1111-1111-1111-111111111111",
            )

            "api://amt-person/.default" in scopes -> mapOf(
                "aud" to listOf("amt-person-service"),
                "sub" to "11111111-1111-1111-1111-111111111111",
                "oid" to "11111111-1111-1111-1111-111111111111",
            )

            "api://amt-altinn/.default" in scopes -> mapOf(
                "aud" to listOf("amt-altinn"),
                "sub" to "11111111-1111-1111-1111-111111111111",
                "oid" to "11111111-1111-1111-1111-111111111111",
                "roles" to listOf("access_as_application"),
            )

            else -> emptyMap()
        }
    }
}

