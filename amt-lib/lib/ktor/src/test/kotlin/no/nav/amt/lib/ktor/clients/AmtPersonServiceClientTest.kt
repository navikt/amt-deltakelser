package no.nav.amt.lib.ktor.clients

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.lib.ktor.clients.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.dto.NavBrukerFodselsarDto
import no.nav.amt.lib.testing.testdata.person.PersonDtoTestsData.brukerDtoInTest
import no.nav.amt.lib.testing.testdata.person.PersonDtoTestsData.enhetDtoInTest
import no.nav.amt.lib.testing.testdata.person.PersonModelsTestData.ansattInTest
import no.nav.amt.lib.testing.testdata.person.PersonModelsTestData.enhetInTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Year
import kotlin.reflect.KClass

class AmtPersonServiceClientTest {
    @Nested
    inner class HentNavAnsattByNavIdent {
        val expectedUrl = "$PERSON_SVC_BASE_URL/api/nav-ansatt"
        val expectedErrorMessage = "Kunne ikke hente nav-ansatt med ident ${ansattInTest.navIdent} fra amt-person-service."
        val hentNavAnsattLambda: suspend (AmtPersonServiceClient) -> NavAnsatt =
            { client -> client.hentNavAnsatt(ansattInTest.navIdent) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.ktor.clients.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedUrl,
                expectedError = expectedErrorMessage,
                block = hentNavAnsattLambda,
            )
        }

        @Test
        fun `skal returnere NavAnsatt`() {
            runHappyPathTest(
                expectedUrl = expectedUrl,
                expectedResponse = ansattInTest,
                block = hentNavAnsattLambda,
            )
        }
    }

    @Nested
    inner class HentNavAnsattById {
        val expectedUrl = "$PERSON_SVC_BASE_URL/api/nav-ansatt/${ansattInTest.id}"
        val expectedErrorMessage = "Kunne ikke hente nav-ansatt med id ${ansattInTest.id} fra amt-person-service."
        val hentNavAnsattLambda: suspend (AmtPersonServiceClient) -> NavAnsatt =
            { client -> client.hentNavAnsatt(ansattInTest.id) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.ktor.clients.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedUrl,
                expectedError = expectedErrorMessage,
                block = hentNavAnsattLambda,
            )
        }

        @Test
        fun `skal returnere NavAnsatt`() {
            runHappyPathTest(expectedUrl = expectedUrl, expectedResponse = ansattInTest, block = hentNavAnsattLambda)
        }
    }

    @Nested
    inner class HentNavEnhetByNavEnhetsnummer {
        val expectedUrl = "$PERSON_SVC_BASE_URL/api/nav-enhet"
        val expectedErrorMessage = "Kunne ikke hente nav-enhet med nummer ${enhetInTest.enhetsnummer} fra amt-person-service."
        val hentNavEnhetLambda: suspend (AmtPersonServiceClient) -> NavEnhet =
            { client -> client.hentNavEnhet(enhetInTest.enhetsnummer) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.ktor.clients.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedUrl,
                expectedError = expectedErrorMessage,
                block = hentNavEnhetLambda,
            )
        }

        @Test
        fun `skal returnere NavEnhet`() {
            runHappyPathTest(
                expectedUrl = expectedUrl,
                expectedResponse = enhetDtoInTest.toModel(),
                responseBody = enhetDtoInTest,
                block = hentNavEnhetLambda,
            )
        }
    }

    @Nested
    inner class HentNavEnhetById {
        val expectedUrl = "$PERSON_SVC_BASE_URL/api/nav-enhet/${enhetDtoInTest.id}"
        val expectedErrorMessage = "Kunne ikke hente nav-enhet med id ${enhetDtoInTest.id} fra amt-person-service."
        val hentNavEnhetLambda: suspend (AmtPersonServiceClient) -> NavEnhet =
            { client -> client.hentNavEnhet(enhetDtoInTest.id) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.ktor.clients.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedUrl,
                expectedError = expectedErrorMessage,
                block = hentNavEnhetLambda,
            )
        }

        @Test
        fun `skal returnere NavEnhet`() {
            runHappyPathTest(
                expectedUrl = expectedUrl,
                expectedResponse = enhetDtoInTest.toModel(),
                responseBody = enhetDtoInTest,
                block = hentNavEnhetLambda,
            )
        }
    }

    @Nested
    inner class HentNavBruker {
        val expectedUrl = "$PERSON_SVC_BASE_URL/api/nav-bruker"
        val expectedErrorMessage = "Kunne ikke hente nav-bruker fra amt-person-service"
        val hentNavBrukerLambda: suspend (AmtPersonServiceClient) -> NavBruker =
            { client -> client.hentNavBruker(brukerDtoInTest.personident) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.ktor.clients.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedUrl,
                expectedError = expectedErrorMessage,
                block = hentNavBrukerLambda,
            )
        }

        @Test
        fun `skal returnere NavBruker`() {
            runHappyPathTest(
                expectedUrl = expectedUrl,
                expectedResponse = brukerDtoInTest.toModel(),
                responseBody = brukerDtoInTest,
                block = hentNavBrukerLambda,
            )
        }
    }

    @Nested
    inner class HentNavBrukerFodselsar {
        val expectedUrl = "${PERSON_SVC_BASE_URL}/api/nav-bruker-fodselsar"
        val expectedErrorMessage = "Kunne ikke hente fodselsar for nav-bruker fra amt-person-service"
        val hentNavBrukerFodselsarLambda: suspend (AmtPersonServiceClient) -> Int =
            { client -> client.hentNavBrukerFodselsar(brukerDtoInTest.personident) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.ktor.clients.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedUrl,
                expectedError = expectedErrorMessage,
                block = hentNavBrukerFodselsarLambda,
            )
        }

        @Test
        fun `skal returnere fodselsar`() {
            val brukerFodselsarDto = NavBrukerFodselsarDto(Year.now().value - 20)
            runHappyPathTest(
                expectedUrl = expectedUrl,
                expectedResponse = brukerFodselsarDto.fodselsar,
                responseBody = brukerFodselsarDto,
                block = hentNavBrukerFodselsarLambda,
            )
        }
    }

    companion object {
        private const val PERSON_SVC_BASE_URL = "http://amt-person-svc"

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedError: String,
            block: suspend (AmtPersonServiceClient) -> Any,
        ) {
            val thrown = assertThrows(exceptionType.java) {
                runBlocking {
                    block(createPersonServiceClient(expectedUrl, statusCode))
                }
            }
            thrown.message shouldStartWith expectedError
        }

        private fun <T : Any> runHappyPathTest(
            expectedUrl: String,
            expectedResponse: T,
            responseBody: Any = expectedResponse,
            block: suspend (AmtPersonServiceClient) -> T,
        ) = runBlocking {
            val personServiceClient = createPersonServiceClient(expectedUrl, HttpStatusCode.OK, responseBody)
            block(personServiceClient) shouldBe expectedResponse
        }

        private fun createPersonServiceClient(
            expectedUrl: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: Any? = null,
        ) = AmtPersonServiceClient(
            baseUrl = PERSON_SVC_BASE_URL,
            scope = "scope",
            httpClient = createMockHttpClient(expectedUrl, responseBody, statusCode),
            azureAdTokenClient = mockk(relaxed = true),
        )
    }
}
