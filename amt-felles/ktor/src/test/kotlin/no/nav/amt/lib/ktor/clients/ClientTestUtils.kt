package no.nav.amt.lib.ktor.clients

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import io.ktor.utils.io.ByteReadChannel
import no.nav.amt.lib.ktor.auth.exceptions.AuthenticationException
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.utils.applicationConfig
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
    ) = HttpClient(MockEngine) {
        install(ContentNegotiation) { jackson { applicationConfig() } }
        engine {
            addHandler { request ->
                request.url.toString() shouldBe expectedUrl

                when (responseBody) {
                    null -> {
                        respond(
                            content = "",
                            status = statusCode,
                        )
                    }

                    is ByteArray -> {
                        respond(
                            content = responseBody,
                            status = statusCode,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString()),
                        )
                    }

                    is String -> {
                        respond(
                            content = ByteReadChannel(responseBody),
                            status = statusCode,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }

                    else -> {
                        respond(
                            content = ByteReadChannel(objectMapper.writeValueAsBytes(responseBody)),
                            status = statusCode,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }
            }
        }
    }
}
