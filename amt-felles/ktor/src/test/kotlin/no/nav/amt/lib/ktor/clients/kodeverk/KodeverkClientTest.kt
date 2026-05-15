package no.nav.amt.lib.ktor.clients.kodeverk

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
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

class KodeverkClientTest {
    @Nested
    inner class HentKodeverk {
        val tiltakskodeInTest = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING

        val hentKodeverkLambda: suspend (KodeverkClient) -> KodeverkResponse =
            { client -> client.hentKodeverk(tiltakskodeInTest) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase

            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = KODEVERK_EXPECTED_URL,
                expectedErrorMessage = "Kunne ikke hente kodeverk for tiltakskode $tiltakskodeInTest fra Mulighetsrommet",
                block = hentKodeverkLambda,
            )
        }

        @Test
        fun `hentKodeverk skal returnere kodeverk`() = runTest {
            runHappyPathTest(
                expectedUrl = KODEVERK_EXPECTED_URL,
                expectedResponse = KodeverkResponse(
                    tiltakskode = tiltakskodeInTest,
                    alternativer = emptyList(),
                ),
                block = hentKodeverkLambda,
            )
        }

        @Test
        fun `skal bruke cache ved andre kall til hentKodeverk`() = runTest {
            val countingCache = CountingCache<Tiltakskode, KodeverkResponse>()

            val kodeverkClient = createKodeverkClient(
                expectedUrl = KODEVERK_EXPECTED_URL,
                responseBody = KodeverkResponse(
                    tiltakskode = tiltakskodeInTest,
                    alternativer = emptyList(),
                ),
                cache = countingCache,
            )

            kodeverkClient.hentKodeverk(tiltakskodeInTest)
            kodeverkClient.hentKodeverk(tiltakskodeInTest)

            countingCache.putCount shouldBe 1
        }
    }

    @Nested
    inner class SertifiseringSok {
        val sertifiseringSokLambda: suspend (KodeverkClient) -> List<SertifiseringResponse> =
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
        private const val KODEVERK_BASE_URL = "http://mulighetsrommet/"

        private const val KODEVERK_EXPECTED_URL =
            "$KODEVERK_BASE_URL/api/kodeverk/opplaring/kategorisering?tiltakskode=ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING"

        private const val SERTIFISERINGER_EXPECTED_URL =
            "$KODEVERK_BASE_URL/api/kodeverk/opplaring/sertifiseringer/sok?q=term"

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedErrorMessage: String,
            block: suspend (KodeverkClient) -> Any,
        ) {
            val thrown = assertThrows(exceptionType.java) {
                runTest { block(createKodeverkClient(expectedUrl, statusCode)) }
            }
            thrown.message shouldStartWith expectedErrorMessage
        }

        private suspend fun <T> runHappyPathTest(
            expectedUrl: String,
            expectedResponse: T,
            block: suspend (KodeverkClient) -> T,
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
            cache: CountingCache<Tiltakskode, KodeverkResponse>? = null,
        ) = if (cache == null) {
            KodeverkClient(
                baseUrl = KODEVERK_BASE_URL,
                scope = "scope",
                httpClient = createMockHttpClient(
                    expectedUrl = expectedUrl,
                    responseBody = responseBody,
                    statusCode = statusCode,
                ),
                azureAdTokenClient = mockAzureAdClient(),
            )
        } else {
            KodeverkClient(
                baseUrl = KODEVERK_BASE_URL,
                scope = "scope",
                httpClient = createMockHttpClient(
                    expectedUrl = expectedUrl,
                    responseBody = responseBody,
                    statusCode = statusCode,
                ),
                azureAdTokenClient = mockAzureAdClient(),
                kodeverkCache = cache,
            )
        }
    }
}
