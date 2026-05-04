package no.nav.amt.lib.ktor.clients.kodeverk

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient
import no.nav.amt.lib.testing.utils.CountingCache
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

class KodeverkClientTest {
    @ParameterizedTest
    @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
    fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
        val (statusCode, expectedExceptionType) = testCase

        runFailureTest(
            exceptionType = expectedExceptionType,
            statusCode = statusCode,
        )
    }

    @Test
    fun `hentKodeverk skal returnere kodeverk`() = runTest {
        // Arrange
        val client = createKodeverkClient(
            responseBody = KodeverkResponse(
                tiltakskode = tiltakskodeInTest,
                alternativer = emptyList(),
            ),
        )

        // Act & Assert
        client.hentKodeverk(tiltakskodeInTest).tiltakskode shouldBe tiltakskodeInTest
    }

    @Test
    fun `skal bruke cache ved andre kall til hentKodeverk`() = runTest {
        val countingCache = CountingCache<Tiltakskode, KodeverkResponse>()

        val kodeverkClient = createKodeverkClient(
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

    companion object {
        private const val KODEVERK_BASE_URL = "http://mulighetsrommet/"
        private val EXPECTED_URL = "$KODEVERK_BASE_URL/api/v1/kodeverk/${Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING}"
        private val tiltakskodeInTest = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
        ) {
            val thrown = Assertions.assertThrows(exceptionType.java) {
                runTest {
                    createKodeverkClient(statusCode).hentKodeverk(tiltakskodeInTest)
                }
            }
            thrown.message shouldStartWith
                "Kunne ikke hente kodeverk for tiltakskode $tiltakskodeInTest fra Mulighetsrommet"
        }

        private fun createKodeverkClient(
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: KodeverkResponse? = null,
            cache: CountingCache<Tiltakskode, KodeverkResponse>? = null,
        ) = if (cache == null) {
            KodeverkClient(
                baseUrl = KODEVERK_BASE_URL,
                scope = "scope",
                httpClient = createMockHttpClient(EXPECTED_URL, responseBody, statusCode),
                azureAdTokenClient = mockAzureAdClient(),
            )
        } else {
            KodeverkClient(
                baseUrl = KODEVERK_BASE_URL,
                scope = "scope",
                httpClient = createMockHttpClient(EXPECTED_URL, responseBody, statusCode),
                azureAdTokenClient = mockAzureAdClient(),
                kodeverkCache = cache,
            )
        }
    }
}
