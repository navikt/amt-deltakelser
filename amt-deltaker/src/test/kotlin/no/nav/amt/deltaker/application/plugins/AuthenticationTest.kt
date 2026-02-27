package no.nav.amt.deltaker.application.plugins

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.generateJWT
import org.junit.jupiter.api.Test

class AuthenticationTest : RouteTestBase() {
    @Test
    fun `testAuthentication - gyldig token, klient-app har tilgang - returnerer 200`() {
        withTestApplicationContext { client ->
            val response = client.get("/deltaker") {
                bearerAuth(bearerTokenInTest)
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "System har tilgang!"
        }
    }

    @Test
    fun `testAuthentication - gyldig token, ikke maskin-til-maskin - returnerer 401`() {
        val response = withTestApplicationContext { client ->
            client.get("/deltaker") {
                bearerAuth(generateJWT(consumerClientId = "amt-deltaker-bff", audience = "amt-deltaker", oid = "ikke-subject"))
            }
        }
        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `testAuthentication - gyldig token, klient-app har ikke tilgang - returnerer 401`() {
        val response = withTestApplicationContext { client ->
            client.get("/deltaker") {
                bearerAuth(generateJWT(consumerClientId = "annen-consumer", audience = "amt-deltaker"))
            }
        }
        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `testAuthentication - gyldig token, feil audience - returnerer 401`() {
        val response = withTestApplicationContext { client ->
            client.get("/deltaker") {
                bearerAuth(generateJWT(consumerClientId = "amt-deltaker-bff", audience = "feil-aud"))
            }
        }

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `testAuthentication - ugyldig tokenissuer - returnerer 401`() {
        val response = withTestApplicationContext { client ->
            client
                .get("/deltaker") {
                    bearerAuth(
                        generateJWT(
                            consumerClientId = "amt-deltaker-bff",
                            audience = "amt-deltaker",
                            issuer = "annenIssuer",
                        ),
                    )
                }
        }

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    companion object {
        private val bearerTokenInTest = generateJWT(
            consumerClientId = "amt-deltaker-bff",
            audience = "amt-deltaker",
        )
    }
}
