package no.nav.amt.lib.testing.utils

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson3.jackson
import io.ktor.utils.io.ByteReadChannel
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.auth.exceptions.AuthenticationException
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.utils.objectMapper

object ClientTestUtils {
    @JvmStatic
    fun failureCases() = listOf(
        Pair(HttpStatusCode.Unauthorized, AuthenticationException::class),
        Pair(HttpStatusCode.Forbidden, AuthorizationException::class),
        Pair(HttpStatusCode.BadRequest, IllegalArgumentException::class),
        Pair(HttpStatusCode.NotFound, NoSuchElementException::class),
        Pair(HttpStatusCode.InternalServerError, IllegalStateException::class),
    )

    fun <T> createMockHttpClient(
        expectedUrl: String,
        responseBody: T?,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        expectAuthHeader: Boolean = true,
        expectedMethod: HttpMethod? = null,
    ) = HttpClient(MockEngine) {
        install(ContentNegotiation) { jackson() }
        engine {
            addHandler { request ->
                request.url.toString() shouldBe expectedUrl
                expectedMethod?.let { request.method shouldBe it }
                if (expectAuthHeader) request.headers[HttpHeaders.Authorization] shouldBe "Bearer XYZ"

                when (responseBody) {
                    null -> respond(
                        content = "",
                        status = statusCode,
                    )

                    is ByteArray -> respond(
                        content = responseBody,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString()),
                    )

                    is String -> respond(
                        content = ByteReadChannel(responseBody),
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )

                    else -> respond(
                        content = ByteReadChannel(objectMapper.writeValueAsBytes(responseBody)),
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
    }

    private const val AZURE_AD_TOKEN_URL = "http://azure"

    fun mockAzureAdClient() = AzureAdTokenClient(
        azureAdTokenUrl = AZURE_AD_TOKEN_URL,
        clientId = "clientId",
        clientSecret = "secret",
        httpClient = createMockHttpClient(
            AZURE_AD_TOKEN_URL,
            """
            {
                "token_type":"Bearer",
                "access_token":"XYZ",
                "expires_in": 3599
            }
            """.trimIndent(),
            expectAuthHeader = false,
        ),
    )
}
