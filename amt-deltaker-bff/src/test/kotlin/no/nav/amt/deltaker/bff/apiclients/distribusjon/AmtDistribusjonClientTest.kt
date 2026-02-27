package no.nav.amt.deltaker.bff.apiclients.distribusjon

import com.github.benmanes.caffeine.cache.Cache
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.utils.createMockHttpClient
import no.nav.amt.deltaker.bff.utils.mockAzureAdClient
import no.nav.amt.lib.testing.utils.CountingCache
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

class AmtDistribusjonClientTest {
    @ParameterizedTest
    @MethodSource("no.nav.amt.deltaker.bff.apiclients.ApiClientTestUtils#failureCases")
    fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
        val (statusCode, expectedExceptionType) = testCase

        val digitalBrukertLambda: suspend (AmtDistribusjonClient) -> Boolean =
            { client -> client.digitalBruker("~personident~") }

        runFailureTest(
            exceptionType = expectedExceptionType,
            statusCode = statusCode,
            digitalBrukertLambda,
        )
    }

    @Test
    fun `digitalBruker skal returnere true`(): Unit = runBlocking {
        val distribusjonClient = createAmtDistribusjonClient(responseBody = DigitalBrukerResponse(erDigital = true))
        val erDigitalBruker = distribusjonClient.digitalBruker("~personident~")
        erDigitalBruker shouldBe true
    }

    @Test
    fun `digitalBruker skal returnere false`(): Unit = runBlocking {
        val distribusjonClient = createAmtDistribusjonClient(responseBody = DigitalBrukerResponse(erDigital = false))
        val erDigitalBruker = distribusjonClient.digitalBruker("~personident~")
        erDigitalBruker shouldBe false
    }

    @Test
    fun `skal bruke cache ved andre kall til digitalBruker`(): Unit = runBlocking {
        val countingCache = CountingCache<String, Boolean>()

        val distribusjonClient = createAmtDistribusjonClient(
            responseBody = DigitalBrukerResponse(erDigital = false),
            cache = countingCache,
        )

        distribusjonClient.digitalBruker("~personident~")
        distribusjonClient.digitalBruker("~personident~")

        countingCache.putCount shouldBe 1
    }

    companion object {
        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            block: suspend (AmtDistribusjonClient) -> Any,
        ) {
            val thrown = Assertions.assertThrows(exceptionType.java) {
                runBlocking {
                    block(createAmtDistribusjonClient(statusCode))
                }
            }
            thrown.message shouldStartWith "Kunne ikke hente om bruker er digital fra amt-distribusjon."
        }

        private fun createAmtDistribusjonClient(
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: DigitalBrukerResponse? = null,
            cache: Cache<String, Boolean>? = null,
        ) = if (cache == null) {
            AmtDistribusjonClient(
                baseUrl = DISTRIBUSJON_BASE_URL,
                scope = "scope",
                httpClient = createMockHttpClient(EXPECTED_URL, responseBody, statusCode),
                azureAdTokenClient = mockAzureAdClient(),
            )
        } else {
            AmtDistribusjonClient(
                baseUrl = DISTRIBUSJON_BASE_URL,
                scope = "scope",
                httpClient = createMockHttpClient(EXPECTED_URL, responseBody, statusCode),
                azureAdTokenClient = mockAzureAdClient(),
                digitalBrukerCache = cache,
            )
        }

        private const val DISTRIBUSJON_BASE_URL = "http://amt-distribusjon"
        private const val EXPECTED_URL = "$DISTRIBUSJON_BASE_URL/digital"
    }
}
