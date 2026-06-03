import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import no.nav.security.mock.oauth2.extensions.clientIdAsString
import no.nav.security.mock.oauth2.extensions.scopesWithoutOidcScopes
import no.nav.security.mock.oauth2.token.OAuth2TokenCallback
import no.nav.security.mock.oauth2.token.RequestMapping
import no.nav.security.mock.oauth2.token.RequestMappingTokenCallback
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.oauth2.sdk.TokenRequest

const val MOCK_OAUTH2_PORT = 9000
const val MOCK_OAUTH2_ISSUER_ID = "azure"

fun startMockOAuth2Server(): MockOAuth2Server {
    val azureMock = SimNavAzureTokenCallback()

    val tokenXMock = RequestMappingTokenCallback(
        issuerId = "tokenx",
        tokenExpiry = 315360000,
        requestMappings = listOf(
            RequestMapping(
                requestParam = "client_id",
                match = "amt-tiltaksarrangor-flate",
                claims = mapOf(
                    "pid" to "01019050188",
                    "aud" to listOf("amt-tiltaksarrangor-bff"),
                    "sub" to "11111111-1111-1111-1111-111111111111",
                ),
            ),
            RequestMapping(
                requestParam = "client_id",
                match = "amt-tiltaksarrangor-bff",
                claims = mapOf(
                    "sub" to "11111111-1111-1111-1111-111111111111",
                    "aud" to listOf("amt-arrangor"),
                ),
            ),
            RequestMapping(
                requestParam = "audience",
                match = "amt-arrangor",
                claims = mapOf(
                    "sub" to "11111111-1111-1111-1111-111111111111",
                    "aud" to listOf("amt-arrangor"),
                ),
            ),
        ),
    )

    return MockOAuth2Server(
        OAuth2Config(
            interactiveLogin = true,
            tokenCallbacks = setOf(azureMock, tokenXMock),
        ),
    ).also {
        it.start(MOCK_OAUTH2_PORT)
    }
}

private class SimNavAzureTokenCallback : OAuth2TokenCallback {
    override fun issuerId(): String = MOCK_OAUTH2_ISSUER_ID

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

        return when {
            clientId == "amt-deltaker-bff" -> mapOf(
                "NAVident" to "Z123456",
                "oid" to "11111111-1111-1111-1111-111111111111",
                "sub" to "11111111-1111-1111-1111-111111111111",
                "azp" to "amt-deltaker-bff",
                "groups" to emptyList<String>(),
                "aud" to listOf("amt-deltaker", "amt-arrangor", "amt-person-service", "amt-distribusjon"),
            )

            clientId == "frontend-client-id" -> mapOf(
                "NAVident" to FrontendAuthState.currentNavIdent(),
                "oid" to "11111111-1111-1111-1111-111111111111",
                "groups" to emptyList<String>(),
                "aud" to listOf("amt-deltaker-bff"),
            )

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

