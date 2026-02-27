package no.nav.amt.lib.ktor.clients.arrangor

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.apiclients.arrangor.ArrangorResponse
import no.nav.amt.lib.ktor.clients.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.TestData
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import kotlin.reflect.KClass

class AmtArrangorClientTest {
    @Nested
    inner class HentArrangorByOrgnummer {
        val expectedUrl = "$ARRANGOR_BASE_URL/api/service/arrangor/organisasjonsnummer/$orgnrInTest"
        val expectedErrorMessage = "Kunne ikke hente arrangør med orgnummer $orgnrInTest"
        val hentArrangorLambda: suspend (AmtArrangorClient) -> ArrangorResponse =
            { client -> client.hentArrangor(orgnrInTest) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.ktor.clients.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, hentArrangorLambda)
        }

        @Test
        fun `skal returnere ArrangorDto`() {
            runHappyPathTest(
                expectedUrl,
                expectedArrangor,
                hentArrangorLambda,
            )
        }
    }

    @Nested
    inner class HentArrangorById {
        val expectedUrl = "$ARRANGOR_BASE_URL/api/service/arrangor/$arrangorIdInTest"
        val expectedErrorMessage = "Kunne ikke hente arrangør med id $arrangorIdInTest"
        val hentArrangorLambda: suspend (AmtArrangorClient) -> ArrangorResponse =
            { client -> client.hentArrangor(arrangorIdInTest) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.ktor.clients.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, hentArrangorLambda)
        }

        @Test
        fun `skal returnere ArrangorDto`() {
            runHappyPathTest(
                expectedUrl,
                expectedArrangor,
                hentArrangorLambda,
            )
        }
    }

    companion object {
        private val orgnrInTest = TestData.randomOrgnr()
        private val arrangorIdInTest: UUID = UUID.randomUUID()
        private const val ARRANGOR_BASE_URL = "http://amt-arrangor"

        val overordnetArrangor = lagArrangor()
        val arrangor = lagArrangor(overordnetArrangorId = overordnetArrangor.id)
        val expectedArrangor =
            ArrangorResponse(arrangor.id, arrangor.navn, arrangor.organisasjonsnummer, overordnetArrangor)

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedError: String,
            block: suspend (AmtArrangorClient) -> Any,
        ) {
            val thrown = Assertions.assertThrows(exceptionType.java) {
                runBlocking {
                    block(createArrangorClient(expectedUrl, statusCode))
                }
            }
            thrown.message shouldStartWith expectedError
        }

        private fun runHappyPathTest(
            expectedUrl: String,
            expectedResponse: ArrangorResponse,
            block: suspend (AmtArrangorClient) -> ArrangorResponse,
        ) = runBlocking {
            val arrangorClient = createArrangorClient(expectedUrl, HttpStatusCode.OK, expectedResponse)

            block(arrangorClient) shouldBe expectedResponse
        }

        private fun createArrangorClient(
            expectedUrl: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: ArrangorResponse? = null,
        ) = AmtArrangorClient(
            baseUrl = ARRANGOR_BASE_URL,
            scope = "scope",
            httpClient = createMockHttpClient(expectedUrl, responseBody, statusCode),
            azureAdTokenClient = mockk(relaxed = true),
        )
    }
}
