package no.nav.amt.distribusjon.application.plugins

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.amt.distribusjon.digitalbruker.api.DigitalBrukerRequest
import no.nav.amt.distribusjon.integrationTest
import no.nav.amt.distribusjon.utils.data.randomIdent
import no.nav.amt.distribusjon.utils.generateJWT
import org.junit.jupiter.api.Test

class AuthenticationTest {
    @Test
    fun `skal returnere Unauthorized naar bearer token mangler ved POST til digital`() {
        val response = performPost()

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `skal returnere OK naar gyldig bearer token brukes ved POST til digital`() {
        val response = performPost(
            bearerToken = generateJWT(
                consumerClientId = "amt-deltaker-bff",
                audience = "amt-distribusjon",
            ),
        )

        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `skal returnere Unauthorized naar oid er forskjellig fra sub i token ved POST til digital`() {
        val response = performPost(
            bearerToken = generateJWT(
                consumerClientId = "amt-deltaker-bff",
                audience = "amt-distribusjon",
                subject = "subject-different-from-oid",
            ),
        )

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `skal returnere Unauthorized naar audience i token er ugyldig ved POST til digital`() {
        val response = performPost(
            bearerToken = generateJWT(
                consumerClientId = "amt-deltaker-bff",
                audience = "ugyldig-audience",
            ),
        )

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    fun performPost(bearerToken: String? = null): HttpResponse {
        lateinit var httpResponse: HttpResponse
        integrationTest { _, httpClient ->
            httpResponse = httpClient.post("/digital") {
                contentType(ContentType.Application.Json)
                setBody(DigitalBrukerRequest(randomIdent()))

                if (bearerToken != null) bearerAuth(bearerToken)
            }
        }
        return httpResponse
    }
}
