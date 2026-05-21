import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import no.nav.security.mock.oauth2.token.RequestMapping
import no.nav.security.mock.oauth2.token.RequestMappingTokenCallback

const val MOCK_OAUTH2_PORT = 9000
const val MOCK_OAUTH2_ISSUER_ID = "azure"

fun startMockOAuth2Server(): MockOAuth2Server {
    val tokenCallback = RequestMappingTokenCallback(
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
                    "aud" to listOf("amt-deltaker", "amt-arrangor"),
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
                match = "api://amt-enhetsregister/.default",
                claims = mapOf(
                    "aud" to listOf("amt-enhetsregister"),
                ),
            ),
        ),
    )

    return MockOAuth2Server(
        OAuth2Config(
            interactiveLogin = true,
            tokenCallbacks = setOf(tokenCallback),
        ),
    ).also { it.start(MOCK_OAUTH2_PORT) }
}

