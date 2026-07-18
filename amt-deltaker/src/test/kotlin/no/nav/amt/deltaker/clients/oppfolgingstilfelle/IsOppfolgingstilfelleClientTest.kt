package no.nav.amt.deltaker.clients.oppfolgingstilfelle

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import kotlin.reflect.KClass

class IsOppfolgingstilfelleClientTest {
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
    fun `erSykmeldtMedArbeidsgiver - ingen oppfolgingstilfeller - returnerer false`() = runTest {
        runHappyPathTest(
            responseBody = OppfolgingstilfellePersonResponse(emptyList()),
            expectedResult = false,
        )
    }

    @Test
    fun `erSykmeldtMedArbeidsgiver - har oppfolgingstilfelle, ikke arbeidsgiver - returnerer false`() = runTest {
        runHappyPathTest(
            responseBody = OppfolgingstilfellePersonResponse(
                listOf(
                    OppfolgingstilfelleDto(
                        arbeidstakerAtTilfelleEnd = false,
                        start = LocalDate.now().minusMonths(3),
                        end = LocalDate.now().plusDays(1),
                    ),
                ),
            ),
            expectedResult = false,
        )
    }

    @Test
    fun `erSykmeldtMedArbeidsgiver - oppfolgingstilfelle avsluttet - returnerer false`() = runTest {
        runHappyPathTest(
            responseBody = OppfolgingstilfellePersonResponse(
                listOf(
                    OppfolgingstilfelleDto(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusMonths(3),
                        end = LocalDate.now().minusDays(1),
                    ),
                ),
            ),
            expectedResult = false,
        )
    }

    @Test
    fun `erSykmeldtMedArbeidsgiver - har oppfolgingstilfelle og arbeidsgiver - returnerer true`() = runTest {
        runHappyPathTest(
            responseBody = OppfolgingstilfellePersonResponse(
                listOf(
                    OppfolgingstilfelleDto(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusMonths(3),
                        end = LocalDate.now().plusWeeks(1),
                    ),
                ),
            ),
            expectedResult = true,
        )
    }

    companion object {
        private const val BASE_URL = "http://isoppfolgingstilfelle"
        private const val EXPECTED_URL = "$BASE_URL/api/system/v1/oppfolgingstilfelle/personident"
        private val personidentInTest = randomIdent()

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
        ) {
            val thrown = assertThrows(exceptionType.java) {
                runBlocking {
                    createIsOppfolgingstilfelleClient(statusCode)
                        .erSykmeldtMedArbeidsgiver(personidentInTest)
                }
            }
            thrown.message shouldStartWith "Kunne ikke hente oppfølgingstilfelle fra isoppfolgingstilfelle."
        }

        private suspend fun runHappyPathTest(
            responseBody: OppfolgingstilfellePersonResponse,
            expectedResult: Boolean,
        ) {
            createIsOppfolgingstilfelleClient(
                statusCode = HttpStatusCode.OK,
                responseBody = responseBody,
            ).erSykmeldtMedArbeidsgiver(personidentInTest) shouldBe expectedResult
        }

        private fun createIsOppfolgingstilfelleClient(
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: Any? = null,
        ) = IsOppfolgingstilfelleClient(
            baseUrl = BASE_URL,
            scope = "scope",
            httpClient = createMockHttpClient(
                expectedUrl = EXPECTED_URL,
                responseBody = responseBody,
                statusCode = statusCode,
            ),
            azureAdTokenClient = mockAzureAdClient(),
        )
    }
}
