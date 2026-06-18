package no.nav.amt.deltaker.bff.clients

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.enkeltplass.OpprettKladdEnkeltplassRequest
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
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
    inner class OpprettKladdTests {
        val request = OpprettKladdEnkeltplassRequest(
            tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            personident = "12345678910",
        )

        val opprettKladdLambda: suspend (EnkeltplassClient) -> DeltakerIdResponse =
            { client ->
                client.opprettKladd(
                    tiltakskode = request.tiltakskode,
                    personident = request.personident,
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) = runTest {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = EXPECTED_OPPRETT_KLADD_URL,
                expectedErrorMessage = "Kunne ikke opprette kladd i amt-deltaker",
                block = opprettKladdLambda,
            )
        }

        @Test
        fun `skal returnere OK`() = runTest {
            runHappyPathTest(
                expectedUrl = EXPECTED_OPPRETT_KLADD_URL,
                expectedResponse = DeltakerIdResponse(deltakerId = deltakerIdInTest),
                block = opprettKladdLambda,
            )
        }
    }

    @Nested
    inner class OppdaterKladdTests {
        val request = OppdaterEnkeltplassKladdRequest(
            beskrivelse = "Testbeskrivelse",
            arrangorUnderenhet = "987654321",
            startdato = null,
            sluttdato = null,
        )

        val oppdaterKladdLambda: suspend (EnkeltplassClient) -> Unit =
            { client -> client.oppdaterKladd(deltakerIdInTest, request) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) = runTest {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedOppdaterKladdUrl,
                expectedErrorMessage = "Kunne ikke oppdatere enkeltplasskladd i amt-deltaker for deltaker $deltakerIdInTest",
                block = oppdaterKladdLambda,
            )
        }

        @Test
        fun `skal returnere OK`() = runTest {
            runHappyPathTest(
                expectedUrl = expectedOppdaterKladdUrl,
                expectedResponse = null,
                block = oppdaterKladdLambda,
            )
        }
    }

    @Nested
    inner class UtkastTests {
        val pameldingRequest = EnkeltplassPameldingRequest(
            beskrivelse = "Testbeskrivelse",
            arrangorUnderenhet = "987654321",
            startdato = null,
            sluttdato = null,
            prisinformasjon = PrisinformasjonDto.Anskaffelse(pris = 1000000),
        )

        private val decoratedRequest = EnkeltplassPameldingDecoratedRequest(
            wrappedRequest = pameldingRequest,
            endretAvEnhet = "1234",
            endretAv = "12345",
        )

        val oppdaterKladdLambda: suspend (EnkeltplassClient) -> DeltakerResponse =
            { client ->
                client.oppdaterUtkast(
                    deltakerId = deltakerIdInTest,
                    pameldingDecoratedRequest = decoratedRequest,
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) = runTest {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedUtkastUrl,
                expectedErrorMessage = "Kunne ikke opprette utkast i amt-deltaker for deltaker $deltakerIdInTest",
                block = oppdaterKladdLambda,
            )
        }

        @Test
        fun `skal returnere OK`() = runTest {
            runHappyPathTest(
                expectedUrl = expectedUtkastUrl,
                expectedResponse = lagDeltakerResponse(
                    id = deltakerIdInTest,
                ),
                block = oppdaterKladdLambda,
            )
        }
    }

    @Nested
    inner class MeldPaaDirekteTests {
        val opprettEnkeltplassLambda: suspend (EnkeltplassClient) -> Unit =
            { client ->
                client.meldPaaDirekte(
                    deltakerId = deltakerIdInTest,
                    pameldingDecoratedRequest = decoratedRequest,
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) = runTest {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedMeldPaaDirekteUrl,
                expectedErrorMessage = "Kunne ikke opprette enkeltplass i amt-deltaker for deltaker $deltakerIdInTest",
                block = opprettEnkeltplassLambda,
            )
        }

        @Test
        fun `skal returnere OK`() = runTest {
            runHappyPathTest(
                expectedUrl = expectedMeldPaaDirekteUrl,
                expectedResponse = null,
                block = opprettEnkeltplassLambda,
            )
        }
    }

    companion object {
        private val deltakerIdInTest = UUID.randomUUID()
        private const val ENKELTPLASS_BASE_URL = "http://amt-deltaker"

        private const val EXPECTED_OPPRETT_KLADD_URL = "$ENKELTPLASS_BASE_URL/enkeltplass/opprett-kladd"
        private val expectedOppdaterKladdUrl = "$ENKELTPLASS_BASE_URL/enkeltplass/oppdater-kladd/$deltakerIdInTest"
        private val expectedUtkastUrl = "$ENKELTPLASS_BASE_URL/enkeltplass/utkast/$deltakerIdInTest"
        private val expectedMeldPaaDirekteUrl =
            "$ENKELTPLASS_BASE_URL/enkeltplass/utkast/$deltakerIdInTest/meld-paa-direkte"

        private val request = EnkeltplassPameldingRequest(
            beskrivelse = "Testbeskrivelse",
            arrangorUnderenhet = "987654322",
            prisinformasjon = PrisinformasjonDto.Anskaffelse(pris = 1000000),
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
