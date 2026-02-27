package no.nav.amt.deltaker.bff.apiclients.paamelding

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.apiclients.DtoMappers.opprettKladdResponseFromDeltaker
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.testdata.OpprettTestDeltakelseRequest
import no.nav.amt.deltaker.bff.testdata.TestdataService.Companion.lagUtkast
import no.nav.amt.deltaker.bff.utils.createMockHttpClient
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.mockAzureAdClient
import no.nav.amt.deltaker.bff.utils.toDeltakeroppdatering
import no.nav.amt.deltaker.bff.utils.toUtkastResponse
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.response.OpprettKladdResponse
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.response.UtkastResponse
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

class PaameldingClientTest {
    @Nested
    inner class OpprettKladd {
        val expectedUrl = "$DELTAKER_BASE_URL/pamelding"
        val expectedErrorMessage = "Kunne ikke opprette kladd i amt-deltaker."
        val opprettKladdLambda: suspend (PaameldingClient) -> OpprettKladdResponse =
            { client -> client.opprettKladd(deltakerlisteId = UUID.randomUUID(), personIdent = "~personident~") }

        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.apiclients.ApiClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, opprettKladdLambda)
        }

        @Test
        fun `skal returnere KladdResponse`() {
            runHappyPathTest(
                expectedUrl,
                opprettKladdResponseFromDeltaker(deltakerInTest),
                opprettKladdLambda,
            )
        }
    }

    @Nested
    inner class Utkast {
        val expectedUrl = "$DELTAKER_BASE_URL/pamelding/${deltakerInTest.id}"
        val expectedErrorMessage = "Kunne ikke oppdatere utkast i amt-deltaker."

        val navBruker = TestData.lagNavBruker(deltakerInTest.id, navEnhetId = UUID.randomUUID())
        val deltakerListe = TestData.lagDeltakerliste()

        val opprettTestDeltakelseRequest = OpprettTestDeltakelseRequest(
            personident = navBruker.personident,
            deltakerlisteId = deltakerListe.id,
            startdato = LocalDate.now().minusDays(1),
            deltakelsesprosent = 60,
            dagerPerUke = 3,
        )

        val utkast = lagUtkast(deltakerInTest.id, deltakerListe, opprettTestDeltakelseRequest)

        val utkastLambda: suspend (PaameldingClient) -> UtkastResponse =
            { client -> client.utkast(utkast) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.apiclients.ApiClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, utkastLambda)
        }

        @Test
        fun `skal returnere UtkastResponse`() {
            runHappyPathTest(expectedUrl, utkastResponseInTest, utkastLambda)
        }
    }

    @Nested
    inner class SlettKladd {
        val expectedUrl = "$DELTAKER_BASE_URL/pamelding/${deltakerInTest.id}"
        val expectedErrorMessage = "Kunne ikke slette kladd i amt-deltaker."
        val slettKladdLambda: suspend (PaameldingClient) -> Unit = { client -> client.slettKladd(deltakerInTest.id) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.apiclients.ApiClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, slettKladdLambda)
        }

        @Test
        fun `skal slette kladd uten feil`() {
            runHappyPathTest(expectedUrl, null, slettKladdLambda)
        }
    }

    @Nested
    inner class AvbrytUtkast {
        val expectedUrl = "$DELTAKER_BASE_URL/pamelding/${deltakerInTest.id}/avbryt"
        val expectedErrorMessage = "Kunne ikke avbryte utkast i amt-deltaker."
        val avbrytUtkastLambda: suspend (PaameldingClient) -> Unit =
            { client ->
                client.avbrytUtkast(
                    deltakerId = deltakerInTest.id,
                    avbruttAv = "~avbruttAv~",
                    avbruttAvEnhet = "~avbruttAvEnhet~",
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.apiclients.ApiClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, avbrytUtkastLambda)
        }

        @Test
        fun `skal avbryte utkast uten feil`() {
            runHappyPathTest(expectedUrl, null, avbrytUtkastLambda)
        }
    }

    @Nested
    inner class InnbyggerGodkjennUtkast {
        val expectedUrl = "$DELTAKER_BASE_URL/pamelding/${deltakerInTest.id}/innbygger/godkjenn-utkast"
        val expectedErrorMessage = "Kunne ikke fatte vedtak i amt-deltaker."
        val innbyggerGodkjennUtkastLambda: suspend (PaameldingClient) -> Deltakeroppdatering =
            { client -> client.innbyggerGodkjennUtkast(deltakerInTest.id) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.apiclients.ApiClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, innbyggerGodkjennUtkastLambda)
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(expectedUrl, deltakerOppdateringInTest, innbyggerGodkjennUtkastLambda)
        }
    }

    companion object {
        private const val DELTAKER_BASE_URL = "http://amt-deltaker"
        private val deltakerInTest = TestData.lagDeltaker()
        private val deltakerOppdateringInTest = deltakerInTest.toDeltakeroppdatering()
        private val utkastResponseInTest = deltakerInTest.toUtkastResponse()

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedError: String,
            block: suspend (PaameldingClient) -> Any,
        ) {
            val thrown = Assertions.assertThrows(exceptionType.java) {
                runBlocking {
                    block(createPaameldingClient(expectedUrl, statusCode))
                }
            }
            thrown.message shouldStartWith expectedError
        }

        private fun <T> runHappyPathTest(
            expectedUrl: String,
            expectedResponse: T,
            block: suspend (PaameldingClient) -> T,
        ) = runBlocking {
            val deltakerClient = createPaameldingClient(expectedUrl, HttpStatusCode.OK, expectedResponse)

            if (expectedResponse == null) {
                shouldNotThrowAny { block(deltakerClient) }
            } else {
                block(deltakerClient) shouldBe expectedResponse
            }
        }

        private fun createPaameldingClient(
            expectedUrl: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: Any? = null,
        ) = PaameldingClient(
            baseUrl = DELTAKER_BASE_URL,
            scope = "scope",
            httpClient = createMockHttpClient(expectedUrl, responseBody, statusCode),
            azureAdTokenClient = mockAzureAdClient(),
        )
    }
}
