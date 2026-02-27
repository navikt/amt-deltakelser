package no.nav.amt.deltaker.bff.apiclients.tiltakskoordinator

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.AvslagRequest
import no.nav.amt.deltaker.bff.utils.createMockHttpClient
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.mockAzureAdClient
import no.nav.amt.deltaker.bff.utils.toDeltakeroppdatering
import no.nav.amt.deltaker.bff.utils.toDeltakeroppdateringResponse
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

class TiltaksKoordinatorClientTest {
    @Nested
    inner class DelMedArrangor {
        val expectedUrl = "$CLIENT_BASE_URL/tiltakskoordinator/deltakere/del-med-arrangor"
        val expectedErrorMessage = "Kunne ikke dele-med-arrangor i amt-deltaker."
        val delMedArrangorLambda: suspend (TiltaksKoordinatorClient) -> List<DeltakerOppdateringResponse> =
            { client ->
                client.delMedArrangor(
                    deltakerIder = listOf(deltakerInTest.id),
                    endretAv = "~endretAv~",
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.apiclients.ApiClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, delMedArrangorLambda)
        }

        @Test
        fun `skal returnere liste med DeltakeroppdateringResponse`() {
            runHappyPathTest(
                expectedUrl,
                listOf(deltakerInTest.toDeltakeroppdateringResponse()),
                delMedArrangorLambda,
            )
        }
    }

    @Nested
    inner class GiAvslag {
        val expectedUrl = "$CLIENT_BASE_URL/tiltakskoordinator/deltakere/gi-avslag"
        val expectedErrorMessage = "Kunne ikke gi avslag i amt-deltaker."
        val avslagRequest = AvslagRequest(
            deltakerId = deltakerInTest.id,
            EndringFraTiltakskoordinator.Avslag.Aarsak(EndringFraTiltakskoordinator.Avslag.Aarsak.Type.ANNET, null),
            null,
        )
        val giAvslagLambda: suspend (TiltaksKoordinatorClient) -> Deltakeroppdatering =
            { client ->
                client.giAvslag(
                    avslagRequest,
                    endretAv = "~endretAv~",
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.apiclients.ApiClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, giAvslagLambda)
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(expectedUrl, deltakerOppdateringInTest, giAvslagLambda)
        }
    }

    companion object {
        private const val CLIENT_BASE_URL = "http://amt-tiltakskoordinator"
        private val deltakerInTest = lagDeltaker()
        private val deltakerOppdateringInTest = deltakerInTest.toDeltakeroppdatering()

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedError: String,
            block: suspend (TiltaksKoordinatorClient) -> Any,
        ) {
            val thrown = Assertions.assertThrows(exceptionType.java) {
                runBlocking {
                    block(createTiltaksKoordinatorClient(expectedUrl, statusCode))
                }
            }
            thrown.message shouldStartWith expectedError
        }

        private fun <T> runHappyPathTest(
            expectedUrl: String,
            expectedResponse: T,
            block: suspend (TiltaksKoordinatorClient) -> T,
        ) = runBlocking {
            val deltakerClient = createTiltaksKoordinatorClient(expectedUrl, HttpStatusCode.OK, expectedResponse)

            if (expectedResponse == null) {
                shouldNotThrowAny { block(deltakerClient) }
            } else {
                block(deltakerClient) shouldBe expectedResponse
            }
        }

        private fun createTiltaksKoordinatorClient(
            expectedUrl: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: Any? = null,
        ) = TiltaksKoordinatorClient(
            baseUrl = CLIENT_BASE_URL,
            scope = "scope",
            httpClient = createMockHttpClient(expectedUrl, responseBody, statusCode),
            azureAdTokenClient = mockAzureAdClient(),
        )
    }
}
