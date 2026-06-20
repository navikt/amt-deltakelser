package no.nav.amt.lib.ktor.clients.kodeverk

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient
import no.nav.amt.lib.testing.utils.CountingCache
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

class OpplaringKategoriseringClientTest {
    @Nested
    inner class HentOpplaringKategoriseringTests {
        val tiltakskodeInTest = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING

        val hentKategoriseringLambda: suspend (OpplaringKategoriseringClient) -> OpplaringKategoriseringResponse =
            { client -> client.hentOpplaringKategorisering(tiltakskodeInTest) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase

            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = KATEGORISERINGER_EXPECTED_URL,
                expectedErrorMessage = "Kunne ikke hente opplæringkategorisering for tiltakskode $tiltakskodeInTest fra Mulighetsrommet",
                block = hentKategoriseringLambda,
            )
        }

        @Test
        fun `skal returnere kategorisering`() = runTest {
            runHappyPathTest(
                expectedUrl = KATEGORISERINGER_EXPECTED_URL,
                expectedResponse = OpplaringKategoriseringResponse(
                    tiltakskode = tiltakskodeInTest,
                    alternativer = emptyList(),
                ),
                block = hentKategoriseringLambda,
            )
        }

        @Test
        fun `skal bruke cache ved andre kall til hentOpplaringKategorisering`() = runTest {
            val countingCache = CountingCache<Tiltakskode, OpplaringKategoriseringResponse>()

            val kodeverkClient = createKodeverkClient(
                expectedUrl = KATEGORISERINGER_EXPECTED_URL,
                responseBody = OpplaringKategoriseringResponse(
                    tiltakskode = tiltakskodeInTest,
                    alternativer = emptyList(),
                ),
                cache = countingCache,
            )

            kodeverkClient.hentOpplaringKategorisering(tiltakskodeInTest)
            kodeverkClient.hentOpplaringKategorisering(tiltakskodeInTest)

            countingCache.putCount shouldBe 1
        }
    }

    @Nested
    inner class SertifiseringSok {
        val sertifiseringSokLambda: suspend (OpplaringKategoriseringClient) -> List<SertifiseringResponse> =
            { client -> client.sertifiseringSok("term") }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase

            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = SERTIFISERINGER_EXPECTED_URL,
                expectedErrorMessage = "Kunne ikke hente sertifiseringer fra Mulighetsrommet",
                block = sertifiseringSokLambda,
            )
        }

        @Test
        fun `sertifiseringSok skal returnere sokeresultat`() = runTest {
            val expectedResponse = listOf(
                SertifiseringResponse(konseptId = 1, label = "Sertifisering 1"),
                SertifiseringResponse(konseptId = 2, label = "Sertifisering 2"),
            )

            runHappyPathTest(
                expectedUrl = SERTIFISERINGER_EXPECTED_URL,
                expectedResponse = expectedResponse,
                block = sertifiseringSokLambda,
            )
        }
    }

    companion object {
        private const val KATEGORISERINGER_BASE_URL = "http://mulighetsrommet/"

        private const val KATEGORISERINGER_EXPECTED_URL =
            "$KATEGORISERINGER_BASE_URL/api/kodeverk/opplaring/kategorisering?tiltakskode=ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING"

        private const val SERTIFISERINGER_EXPECTED_URL =
            "$KATEGORISERINGER_BASE_URL/api/kodeverk/opplaring/sertifiseringer/sok?q=term"

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedErrorMessage: String,
            block: suspend (OpplaringKategoriseringClient) -> Any,
        ) {
            val thrown = assertThrows(exceptionType.java) {
                runTest { block(createKodeverkClient(expectedUrl, statusCode)) }
            }
            thrown.message shouldStartWith expectedErrorMessage
        }

        private suspend fun <T> runHappyPathTest(
            expectedUrl: String,
            expectedResponse: T,
            block: suspend (OpplaringKategoriseringClient) -> T,
        ) {
            val kodeverkClient = createKodeverkClient(
                expectedUrl = expectedUrl,
                statusCode = HttpStatusCode.OK,
                responseBody = expectedResponse,
            )

            if (expectedResponse == null) {
                shouldNotThrowAny { block(kodeverkClient) }
            } else {
                block(kodeverkClient) shouldBe expectedResponse
            }
        }

        private fun createKodeverkClient(
            expectedUrl: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: Any? = null,
            cache: CountingCache<Tiltakskode, OpplaringKategoriseringResponse>? = null,
        ) = if (cache == null) {
            OpplaringKategoriseringClient(
                baseUrl = KATEGORISERINGER_BASE_URL,
                scope = "scope",
                httpClient = createMockHttpClient(
                    expectedUrl = expectedUrl,
                    responseBody = responseBody,
                    statusCode = statusCode,
                ),
                azureAdTokenClient = mockAzureAdClient(),
            )
        } else {
            OpplaringKategoriseringClient(
                baseUrl = KATEGORISERINGER_BASE_URL,
                scope = "scope",
                httpClient = createMockHttpClient(
                    expectedUrl = expectedUrl,
                    responseBody = responseBody,
                    statusCode = statusCode,
                ),
                azureAdTokenClient = mockAzureAdClient(),
                kategoriseringCache = cache,
            )
        }
    }
}
