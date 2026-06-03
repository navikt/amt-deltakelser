import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import no.nav.security.mock.oauth2.token.RequestMapping
import no.nav.security.mock.oauth2.token.RequestMappingTokenCallback

const val MOCK_OAUTH2_PORT = 9000
const val MOCK_OAUTH2_ISSUER_ID = "azure"

fun startMockOAuth2Server(): MockOAuth2Server {
    val azureMock = RequestMappingTokenCallback(
        issuerId = MOCK_OAUTH2_ISSUER_ID,
        tokenExpiry = 315360000,
        requestMappings = listOf(
            RequestMapping(
                requestParam = "client_id",
                match = "amt-deltaker-bff",
                claims = mapOf(
                    "NAVident" to "Z123456",
                    "oid" to "11111111-1111-1111-1111-111111111111",
                    "sub" to "11111111-1111-1111-1111-111111111111",
                    "azp" to "amt-deltaker-bff",
                    "groups" to emptyList<String>(),
                    "aud" to listOf("amt-deltaker", "amt-arrangor", "amt-person-service", "amt-distribusjon"),
                ),
            ),
            RequestMapping(
                requestParam = "client_id",
                match = "frontend-client-id",
                claims = mapOf(
                    "NAVident" to "Z123456",
                    "oid" to "11111111-1111-1111-1111-111111111111",
                    "groups" to emptyList<String>(),
                    "aud" to listOf("amt-deltaker-bff"),
                ),
            ),
            RequestMapping(
                requestParam = "scope",
                match = "api://amt-distribusjon/.default",
                claims = mapOf(
                    "aud" to listOf("amt-distribusjon"),
                    "sub" to "11111111-1111-1111-1111-111111111111",
                    "oid" to "11111111-1111-1111-1111-111111111111",
                    "azp" to "amt-deltaker",
                ),
            ),
            RequestMapping(
                requestParam = "scope",
                match = "api://amt-enhetsregister/.default",
                claims = mapOf(
                    "aud" to listOf("amt-enhetsregister"),
                    "sub" to "11111111-1111-1111-1111-111111111111",
                ),
            ),
            RequestMapping(
                requestParam = "scope",
                match = "api://amt-person/.default",
                claims = mapOf(
                    "aud" to listOf("amt-person-service"),
                    "sub" to "11111111-1111-1111-1111-111111111111",
                    "oid" to "11111111-1111-1111-1111-111111111111",
                ),
            ),
            RequestMapping(
                requestParam = "scope",
                match = "api://amt-altinn/.default",
                claims = mapOf(
                    "aud" to listOf("amt-altinn"),
                    "sub" to "11111111-1111-1111-1111-111111111111",
                    "oid" to "11111111-1111-1111-1111-111111111111",
                    "roles" to listOf("access_as_application")
                ),
            ),
        ),
    )

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

