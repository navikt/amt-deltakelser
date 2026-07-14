package no.nav.amt.deltaker.bff.clients

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.clients.arrangorsok.ArrangorsokClient
import no.nav.amt.deltaker.bff.clients.arrangorsok.EnhetResponse
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

class ArrangorsokClientTest {
    @Nested
    inner class UnderenhetSokTests {
        val underenhetSokLambda: suspend (ArrangorsokClient) -> List<EnhetResponse> =
            { client ->
                client.underenhetSok(term = "term with space")
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                block = underenhetSokLambda,
            )
        }

        @Test
        fun `skal returnere underenheter`() {
            runHappyPathTest(
                expectedResponse = listOf(
                    EnhetResponse(
                        organisasjonsnummer = "987654321",
                        organisasjonsform = "AS",
                        navn = "Firma AS",
                        overordnetEnhet = "987654321",
                    ),
                ),
                block = underenhetSokLambda,
            )
        }
    }

    companion object {
        private const val ARRANGORSOK_BASE_URL = "http://arrangorsok"
        private const val EXPECTED_UNDERENHET_SOK_URL = "$ARRANGORSOK_BASE_URL/api/v1/virksomhet/underenhet?sok=term+with+space"

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            block: suspend (ArrangorsokClient) -> Any,
        ) {
            val thrown = Assertions.assertThrows(exceptionType.java) {
                runBlocking {
                    block(createArrangorsokClient(EXPECTED_UNDERENHET_SOK_URL, statusCode))
                }
            }
            thrown.message shouldStartWith "Kunne ikke hente underenheter fra Mulighetsrommet"
        }

        private fun <T> runHappyPathTest(
            expectedResponse: T,
            block: suspend (ArrangorsokClient) -> T,
        ) = runBlocking {
            val arrangorsokClient = createArrangorsokClient(
                expectedUrl = EXPECTED_UNDERENHET_SOK_URL,
                statusCode = HttpStatusCode.OK,
                responseBody = expectedResponse,
            )

            if (expectedResponse == null) {
                shouldNotThrowAny { block(arrangorsokClient) }
            } else {
                block(arrangorsokClient) shouldBe expectedResponse
            }
        }

        private fun createArrangorsokClient(
            expectedUrl: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: Any? = null,
        ) = ArrangorsokClient(
            baseUrl = ARRANGORSOK_BASE_URL,
            scope = "scope",
            httpClient = createMockHttpClient(
                expectedUrl = expectedUrl,
                responseBody = responseBody,
                statusCode = statusCode,
            ),
            azureAdTokenClient = mockAzureAdClient(),
        )
    }
}
