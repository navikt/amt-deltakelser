package no.nav.amt.distribusjon.amtdeltaker

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.distribusjon.utils.data.DeltakerData.lagDeltakerResponse
import no.nav.amt.distribusjon.utils.data.Hendelsesdata.lagDeltaker
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

class AmtDeltakerClientTest {
    @Test
    fun `skal returnere DeltakerResponse`() {
        runHappyPathTest(
            expectedUrl = expectedUrl,
            expectedResponse = lagDeltakerResponse(),
            block = getDeltakerLambda,
        )
    }

    @ParameterizedTest
    @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
    fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
        val (statusCode, expectedExceptionType) = testCase
        runFailureTest(
            exceptionType = expectedExceptionType,
            statusCode = statusCode,
            expectedUrl = expectedUrl,
            expectedErrorMessage = expectedErrorMessage,
            block = getDeltakerLambda,
        )
    }

    companion object {
        private const val DELTAKER_BASE_URL = "http://amt-deltaker"
        private val deltakerInTest = lagDeltaker()
        val expectedUrl = "${DELTAKER_BASE_URL}/deltaker/${deltakerInTest.id}"
        val expectedErrorMessage = "Fant ikke deltaker ${deltakerInTest.id} i amt-deltaker."
        val getDeltakerLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client -> client.getDeltaker(deltakerInTest.id) }

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedErrorMessage: String,
            block: suspend (AmtDeltakerClient) -> Any,
        ) {
            val thrown = assertThrows(exceptionType.java) {
                runTest {
                    block(
                        createDeltakerClient(expectedUrl, statusCode),
                    )
                }
            }
            thrown.message shouldStartWith expectedErrorMessage
        }

        private fun <T> runHappyPathTest(
            expectedUrl: String,
            expectedResponse: T,
            block: suspend (AmtDeltakerClient) -> T,
        ) = runTest {
            val deltakerClient = createDeltakerClient(
                expectedUrl = expectedUrl,
                statusCode = HttpStatusCode.OK,
                responseBody = expectedResponse,
            )

            if (expectedResponse == null) {
                shouldNotThrowAny { block(deltakerClient) }
            } else {
                block(deltakerClient) shouldBe expectedResponse
            }
        }

        private fun createDeltakerClient(
            expectedUrl: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: Any? = null,
        ) = AmtDeltakerClient(
            baseUrl = DELTAKER_BASE_URL,
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
