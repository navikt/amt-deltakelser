package no.nav.amt.deltaker.bff.veileder.api

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.clients.arrangorsok.EnhetResponse
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ArrangorsokApiTest : IntegrationTestBase() {
    companion object {
        private const val ORGNUMMER_IN_TEST = "987654321"
    }

    @Nested
    inner class UnderenhetSokTests {
        @Test
        fun `skal returnere Unauthorized nar tilgang mangler`() {
            // Act
            val response = withTestApplicationContext { client ->
                client.get("/arrangor/underenhet/sok/firma")
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

            coEvery { arrangorsokClient.underenhetSok(any()) } returns expectedResponse

            // Act
            val response = withTestApplicationContext { client ->
                client.get("/arrangor/underenhet/sok/foo") {
                    bearerAuth(bearerTokenInTest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK
            response.body<List<EnhetResponse>>() shouldBe expectedResponse
        }
    }
}
