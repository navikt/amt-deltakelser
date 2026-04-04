package no.nav.amt.deltaker.bff.apiclients

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import kotlin.reflect.KClass

class EnkeltplassClientTest {
    @Nested
    inner class OpprettEnkeltplassTests {
        val opprettEnkeltplassLambda: suspend (EnkeltplassClient) -> Unit =
            { client -> client.meldPaaDirekte(deltakerIdInTest, decoratedRequest) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) = runTest {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedOpprettEnkeltplassUrl,
                expectedErrorMessage = "Kunne ikke opprette enkeltplass i amt-deltaker for deltaker $deltakerIdInTest",
                block = opprettEnkeltplassLambda,
            )
        }

        @Test
        fun `skal returnere OK`() = runTest {
            runHappyPathTest(
                expectedUrl = expectedOpprettEnkeltplassUrl,
                expectedResponse = null,
                block = opprettEnkeltplassLambda,
            )
        }
    }

    companion object {
        private val deltakerIdInTest = UUID.randomUUID()
        private const val ENKELTPLASS_BASE_URL = "http://amt-deltaker"

        private val expectedOpprettEnkeltplassUrl =
            "$ENKELTPLASS_BASE_URL/enkeltplass/utkast/$deltakerIdInTest/meld-paa-direkte"

        private val request = EnkeltplassPameldingRequest(
            beskrivelse = "Testbeskrivelse",
            prisinformasjon = "Test prisinformasjon",
            arrangorOrgnummer = "987654321",
        )

        private val decoratedRequest = EnkeltplassPameldingDecoratedRequest(
            wrappedRequest = request,
            endretAvEnhet = "1234",
            endretAv = "12345",
        )

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedErrorMessage: String,
            block: suspend (EnkeltplassClient) -> Any,
        ) {
            val thrown = assertThrows(exceptionType.java) {
                runTest { block(createEnkeltplassClient(expectedUrl, statusCode)) }
            }
            thrown.message shouldStartWith expectedErrorMessage
        }

        private suspend fun <T> runHappyPathTest(
            expectedUrl: String,
            expectedResponse: T,
            block: suspend (EnkeltplassClient) -> T,
        ) {
            val enkeltplassClient = createEnkeltplassClient(
                expectedUrl = expectedUrl,
                statusCode = HttpStatusCode.OK,
                responseBody = expectedResponse,
            )

            if (expectedResponse == null) {
                shouldNotThrowAny { block(enkeltplassClient) }
            } else {
                block(enkeltplassClient) shouldBe expectedResponse
            }
        }

        private fun createEnkeltplassClient(
            expectedUrl: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: Any? = null,
        ) = EnkeltplassClient(
            baseUrl = ENKELTPLASS_BASE_URL,
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
