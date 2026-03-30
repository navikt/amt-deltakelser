package no.nav.amt.deltaker.bff.apiclients.arrangorsok

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.utils.createMockHttpClient
import no.nav.amt.deltaker.bff.utils.mockAzureAdClient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

class ArrangorsokClientTest {
    @Nested
    inner class HovedenhetSokTests {
        val hovedenhetSokLambda: suspend (ArrangorsokClient) -> List<EnhetResponse> =
            { client ->
                client.hovedenhetSok(term = "term")
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.apiclients.ApiClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = EXPECTED_HOVEDENHET_SOK_URL,
                expectedErrorMessage = "Kunne ikke hente hovedenheter fra Mulighetsrommet",
                block = hovedenhetSokLambda,
            )
        }

        @Test
        fun `skal returnere hovedenheter`() {
            runHappyPathTest(
                expectedUrl = EXPECTED_HOVEDENHET_SOK_URL,
                expectedResponse = listOf(
                    EnhetResponse(
                        organisasjonsnummer = ORGNUM_IN_TEST,
                        organisasjonsform = "AS",
                        navn = "Firma AS",
                        overordnetEnhet = null,
                    ),
                ),
                block = hovedenhetSokLambda,
            )
        }
    }

    @Nested
    inner class HentUnderenheterTests {
        val hentUnderenheterLambda: suspend (ArrangorsokClient) -> List<EnhetResponse> =
            { client ->
                client.hentUnderenheter(orgnummer = ORGNUM_IN_TEST)
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.apiclients.ApiClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = EXPECTED_UNDERENHET_URL,
                expectedErrorMessage = "Kunne ikke hente underenheter fra Mulighetsrommet for orgnummer $ORGNUM_IN_TEST",
                block = hentUnderenheterLambda,
            )
        }

        @Test
        fun `skal returnere underenheter`() {
            runHappyPathTest(
                expectedUrl = EXPECTED_UNDERENHET_URL,
                expectedResponse = listOf(
                    EnhetResponse(
                        organisasjonsnummer = "888888888",
                        organisasjonsform = "AS",
                        navn = "Firma AS - avd. Oslo",
                        overordnetEnhet = ORGNUM_IN_TEST,
                    ),
                ),
                block = hentUnderenheterLambda,
            )
        }
    }

    companion object {
        private const val ORGNUM_IN_TEST = "987654321"
        private const val ARRANGORSOK_BASE_URL = "http://arrangorsok"
        private const val EXPECTED_HOVEDENHET_SOK_URL = "$ARRANGORSOK_BASE_URL/api/v1/arrangor/hovedenhet/sok/term"
        private const val EXPECTED_UNDERENHET_URL = "$ARRANGORSOK_BASE_URL/api/v1/arrangor/hovedenhet/$ORGNUM_IN_TEST/underenheter"

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedErrorMessage: String,
            block: suspend (ArrangorsokClient) -> Any,
        ) {
            val thrown = Assertions.assertThrows(exceptionType.java) {
                runBlocking {
                    block(createArrangorsokClient(expectedUrl, statusCode))
                }
            }
            thrown.message shouldStartWith expectedErrorMessage
        }

        private fun <T> runHappyPathTest(
            expectedUrl: String,
            expectedResponse: T,
            block: suspend (ArrangorsokClient) -> T,
        ) = runBlocking {
            val arrangorsokClient = createArrangorsokClient(
                expectedUrl = expectedUrl,
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
