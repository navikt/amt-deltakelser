package no.nav.amt.deltaker.bff.veileder.api

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.apiclients.arrangorsok.EnhetResponse
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ArrangorsokApiTest : IntegrationTestBase() {
    companion object {
        private const val ORGNUMMER_IN_TEST = "987654321"
    }

    @Nested
    inner class HovedenhetSokTests {
        @Test
        fun `skal returnere Unauthorized nar tilgang mangler`() {
            // Act
            val response = withTestApplicationContext { client ->
                client.get("/arrangor/hovedenhet/sok/firma")
            }

            // Assert
            response.status shouldBe HttpStatusCode.Unauthorized
        }

        @Test
        fun `skal returnere sokeresultat`() = runTest {
            // Arrange
            val expectedResponse = listOf(
                EnhetResponse(
                    organisasjonsnummer = ORGNUMMER_IN_TEST,
                    organisasjonsform = "AS",
                    navn = "Firma AS",
                    overordnetEnhet = null,
                ),
            )

            coEvery { arrangorsokClient.hovedenhetSok(any()) } returns expectedResponse

            // Act
            val response = withTestApplicationContext { client ->
                client.get("/arrangor/hovedenhet/sok/foo") {
                    bearerAuth(bearerTokenInTest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK
            response.body<List<EnhetResponse>>() shouldBe expectedResponse
        }
    }

    @Nested
    inner class HentUnderenheterTests {
        @Test
        fun `skal returnere Unauthorized nar tilgang mangler`() {
            // Act
            val response = withTestApplicationContext { client ->
                client.get("/arrangor/hovedenhet/$ORGNUMMER_IN_TEST/underenheter")
            }

            // Assert
            response.status shouldBe HttpStatusCode.Unauthorized
        }

        @Test
        fun `skal returnere BadRequest nar ugyldig organisasjonsnummer`() {
            // Act
            val response = withTestApplicationContext { client ->
                client.get("/arrangor/hovedenhet/123/underenheter") {
                    bearerAuth(bearerTokenInTest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.BadRequest
        }

        @Test
        fun `skal returnere underenheter`() = runTest {
            // Arrange
            val expectedResponse = listOf(
                EnhetResponse(
                    organisasjonsnummer = "888888888",
                    organisasjonsform = "AS",
                    navn = "Firma AS",
                    overordnetEnhet = ORGNUMMER_IN_TEST,
                ),
            )

            coEvery { arrangorsokClient.hentUnderenheter(any()) } returns expectedResponse

            // Act
            val response = withTestApplicationContext { client ->
                client.get("/arrangor/hovedenhet/$ORGNUMMER_IN_TEST/underenheter") {
                    bearerAuth(bearerTokenInTest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK
            response.body<List<EnhetResponse>>() shouldBe expectedResponse
        }
    }
}
