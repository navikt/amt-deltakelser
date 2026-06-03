package no.nav.amt.deltaker.bff.clients

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.deltaker.request.AvbrytDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.internapi.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.internapi.deltaker.request.EndretInnholdRequest
import no.nav.amt.internapi.deltaker.request.EndringRequest
import no.nav.amt.internapi.deltaker.request.FjernOppstartsdatoRequest
import no.nav.amt.internapi.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.IkkeAktuellRequest
import no.nav.amt.internapi.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.SluttarsakRequest
import no.nav.amt.internapi.deltaker.request.SluttdatoRequest
import no.nav.amt.internapi.deltaker.request.StartdatoRequest
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient
import no.nav.amt.lib.testing.utils.CountingCache
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.withLogCapture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.reflect.KClass

class AmtDeltakerClientTest {
    @Nested
    inner class GetPersonidentForDeltaker {
        val expectedUrl = "$DELTAKER_BASE_URL/personident/deltaker/${deltakerInTest.id}"
        val expectedErrorMessage = "Fant ikke personident for deltaker ${deltakerInTest.id} i amt-deltaker."
        val getPersonidentLambda: suspend (AmtDeltakerClient) -> String =
            { client -> client.getPersonidentForDeltaker(deltakerInTest.id) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedUrl,
                expectedErrorMessage = expectedErrorMessage,
                block = getPersonidentLambda,
            )
        }

        @Test
        fun `skal returnere personident`() = runTest {
            val client = createDeltakerClient(
                expectedUrl = expectedUrl,
                statusCode = HttpStatusCode.OK,
                responseBody = PersonIdentResponse("12345678901"),
            )

            client.getPersonidentForDeltaker(deltakerInTest.id) shouldBe "12345678901"
        }

        @Test
        fun `skal bruke cache ved gjentatt kall for samme deltaker`() = runTest {
            val countingCache = CountingCache<UUID, String>()
            val client = createDeltakerClient(
                expectedUrl = expectedUrl,
                statusCode = HttpStatusCode.OK,
                responseBody = PersonIdentResponse("12345678901"),
                personIdentCache = countingCache,
            )

            val first = client.getPersonidentForDeltaker(deltakerInTest.id)
            val second = client.getPersonidentForDeltaker(deltakerInTest.id)

            first shouldBe "12345678901"
            second shouldBe "12345678901"
            countingCache.putCount shouldBe 1
        }
    }

    @Nested
    inner class GetDeltaker {
        val expectedUrl = "$DELTAKER_BASE_URL/deltaker/${deltakerInTest.id}"
        val expectedErrorMessage = "Fant ikke deltaker ${deltakerInTest.id} i amt-deltaker."
        val getDeltakerLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client -> client.getDeltaker(deltakerInTest.id) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, getDeltakerLambda)
        }

        @Test
        fun `skal returnere DeltakerResponse`() {
            runHappyPathTest(
                expectedUrl = expectedUrl,
                expectedResponse = lagDeltakerResponse(deltakerInTest.id),
                block = getDeltakerLambda,
            )
        }
    }

    @Nested
    inner class EndreBakgrunnsinformasjon {
        val endreBakgrunnsinformasjonLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    requestBody = BakgrunnsinformasjonRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        bakgrunnsinformasjon = "~bakgrunnsinformasjon~",
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<BakgrunnsinformasjonRequest>(),
                block = endreBakgrunnsinformasjonLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                block = endreBakgrunnsinformasjonLambda,
            )
        }
    }

    @Nested
    inner class EndreInnhold {
        val innhold = Deltakelsesinnhold(null, emptyList())
        val endreInnholdLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    requestBody = EndretInnholdRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        innholdselementer = emptyList(),
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<EndretInnholdRequest>(),
                block = endreInnholdLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                block = endreInnholdLambda,
            )
        }
    }

    @Nested
    inner class EndreDeltakelsesmengde {
        val endreDeltakelsesmengdeLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    requestBody = DeltakelsesmengdeRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        deltakelsesprosent = null,
                        dagerPerUke = null,
                        gyldigFra = null,
                        begrunnelse = null,
                        forslagId = null,
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<DeltakelsesmengdeRequest>(),
                block = endreDeltakelsesmengdeLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                block = endreDeltakelsesmengdeLambda,
            )
        }
    }

    @Nested
    inner class EndreStartdato {
        val endreStartdatoLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    requestBody = StartdatoRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        startdato = null,
                        sluttdato = null,
                        begrunnelse = null,
                        forslagId = null,
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<StartdatoRequest>(),
                block = endreStartdatoLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                block = endreStartdatoLambda,
            )
        }
    }

    @Nested
    inner class EndreSluttdato {
        val endreSluttdatoLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    requestBody = SluttdatoRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        sluttdato = LocalDate.now(),
                        begrunnelse = null,
                        forslagId = null,
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<SluttdatoRequest>(),
                block = endreSluttdatoLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                block = endreSluttdatoLambda,
            )
        }
    }

    @Nested
    inner class EndreSluttaarsak {
        val endreSluttaarsakLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    requestBody = SluttarsakRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        aarsak = DeltakerEndring.Aarsak(
                            DeltakerEndring.Aarsak.Type.ANNET,
                            "~beskrivelse~",
                        ),
                        begrunnelse = null,
                        forslagId = null,
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<SluttarsakRequest>(),
                block = endreSluttaarsakLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                endreSluttaarsakLambda,
            )
        }
    }

    @Nested
    inner class ForlengDeltakelse {
        val forlengDeltakelseLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    requestBody = ForlengDeltakelseRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        sluttdato = LocalDate.now(),
                        begrunnelse = null,
                        forslagId = null,
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<ForlengDeltakelseRequest>(),
                block = forlengDeltakelseLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                forlengDeltakelseLambda,
            )
        }
    }

    @Nested
    inner class IkkeAktuell {
        val ikkeAktuellLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    requestBody = IkkeAktuellRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        aarsak = DeltakerEndring.Aarsak(
                            DeltakerEndring.Aarsak.Type.ANNET,
                            "~beskrivelse~",
                        ),
                        begrunnelse = null,
                        forslagId = null,
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<IkkeAktuellRequest>(),
                block = ikkeAktuellLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                block = ikkeAktuellLambda,
            )
        }
    }

    @Nested
    inner class ReaktiverDeltakelse {
        val reaktiverDeltakelseLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    requestBody = ReaktiverDeltakelseRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        begrunnelse = "~begrunnelse~",
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<ReaktiverDeltakelseRequest>(),
                block = reaktiverDeltakelseLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                block = reaktiverDeltakelseLambda,
            )
        }
    }

    @Nested
    inner class AvsluttDeltakelse {
        val avsluttDeltakelseLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    requestBody = AvsluttDeltakelseRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        sluttdato = LocalDate.now(),
                        aarsak = DeltakerEndring.Aarsak(
                            DeltakerEndring.Aarsak.Type.ANNET,
                            "~beskrivelse~",
                        ),
                        begrunnelse = null,
                        forslagId = null,
                        harFullfort = null,
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<AvsluttDeltakelseRequest>(),
                block = avsluttDeltakelseLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                block = avsluttDeltakelseLambda,
            )
        }
    }

    @Nested
    inner class AvbrytDeltakelse {
        val avbrytDeltakelseLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    AvbrytDeltakelseRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        sluttdato = LocalDate.now(),
                        aarsak = DeltakerEndring.Aarsak(
                            DeltakerEndring.Aarsak.Type.ANNET,
                            "~beskrivelse~",
                        ),
                        begrunnelse = null,
                        forslagId = null,
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<AvbrytDeltakelseRequest>(),
                block = avbrytDeltakelseLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                block = avbrytDeltakelseLambda,
            )
        }
    }

    @Nested
    inner class FjernOppstartsdato {
        val fjernOppstartsdatoLambda: suspend (AmtDeltakerClient) -> DeltakerResponse =
            { client ->
                client.postEndreDeltaker(
                    deltakerId = deltakerInTest.id,
                    FjernOppstartsdatoRequest(
                        endretAv = "~endretAv~",
                        endretAvEnhet = "~endretAvEnhet~",
                        begrunnelse = null,
                        forslagId = null,
                    ),
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                exceptionType = expectedExceptionType,
                statusCode = statusCode,
                expectedUrl = expectedEndreDeltakerUrl,
                expectedErrorMessage = createExpectedErrorMessage<FjernOppstartsdatoRequest>(),
                block = fjernOppstartsdatoLambda,
            )
        }

        @Test
        fun `skal returnere Deltakeroppdatering`() {
            runHappyPathTest(
                expectedUrl = expectedEndreDeltakerUrl,
                expectedResponse = deltakerEndringResponseInTest,
                block = fjernOppstartsdatoLambda,
            )
        }
    }

    @Nested
    inner class SistBesokt {
        val expectedUrl = "$DELTAKER_BASE_URL/deltaker/${deltakerInTest.id}/sist-besokt"

        @Test
        fun `skal logge warning ved feil`() {
            val deltakerClient = createDeltakerClient(expectedUrl, HttpStatusCode.Unauthorized)

            withLogCapture("no.nav.amt.deltaker.bff.clients.AmtDeltakerClient") { logEvents ->
                deltakerClient.sistBesokt(deltakerInTest.id, ZonedDateTime.now())

                val logEntry = logEvents.find { it.level.levelStr == "WARN" }
                logEntry.shouldNotBeNull()
                logEntry.message shouldStartWith "Kunne ikke endre sist-besokt i amt-deltaker"
            }
        }

        @Test
        fun `skal ikke kaste feil nar sistBesokt kalles`() {
            runHappyPathTest(
                expectedUrl = expectedUrl,
                expectedResponse = null,
            ) { deltakerClient ->
                deltakerClient.sistBesokt(deltakerInTest.id, ZonedDateTime.now())
            }
        }
    }

    @Nested
    inner class Historikk {
        val expectedUrl = "$DELTAKER_BASE_URL/deltaker/${deltakerInTest.id}/historikk"
        val expectedErrorMessage = "Fant ikke historikkdata for ${deltakerInTest.id} i amt-deltaker."
        val historikk = TestData.leggTilHistorikk(deltakerInTest, 2, 2, 1).historikk

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            val thrown = Assertions.assertThrows(expectedExceptionType.java) {
                runTest {
                    createDeltakerClient(
                        expectedUrl = expectedUrl,
                        statusCode = statusCode,
                        responseBody = "feil",
                    ).getDeltakerHistorikkData(deltakerInTest.id)
                }
            }
            thrown.message shouldStartWith expectedErrorMessage
        }

        @Test
        fun `skal returnere DeltakerHistorikkData`() {
            val navAnsatt = lagNavAnsatt()
            val navEnhet = lagNavEnhet()
            val response = DeltakerHistorikkDataResponse(
                historikk = historikk,
                arrangornavn = "Test Arrangør",
                oppstartstype = Oppstartstype.LOPENDE,
                ansatte = mapOf(navAnsatt.id to navAnsatt),
                enheter = mapOf(navEnhet.id to navEnhet),
                pameldingstype = null,
            )

            val amtDeltakerClient = createDeltakerClient(
                expectedUrl = expectedUrl,
                responseBody = response,
            )

            runTest {
                val result = amtDeltakerClient.getDeltakerHistorikkData(deltakerInTest.id)
                result.historikk shouldBe historikk
                result.arrangornavn shouldBe response.arrangornavn
                result.oppstartstype shouldBe response.oppstartstype
                result.ansatte shouldBe mapOf(navAnsatt.id to navAnsatt)
                result.enheter shouldBe mapOf(navEnhet.id to navEnhet)
            }
        }
    }

    companion object {
        private const val DELTAKER_BASE_URL = "http://amt-deltaker"
        private val deltakerInTest = lagDeltaker()
        private val deltakerEndringResponseInTest = lagDeltakerResponse(deltakerInTest)
        private val expectedEndreDeltakerUrl = "$DELTAKER_BASE_URL/deltaker/${deltakerInTest.id}/endre-deltaker"

        private inline fun <reified T : EndringRequest> createExpectedErrorMessage() =
            "Kunne ikke oppdatere deltaker ${deltakerInTest.id} med ${T::class.simpleName} i amt-deltaker"

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedErrorMessage: String,
            block: suspend (AmtDeltakerClient) -> Any,
        ) {
            val thrown = Assertions.assertThrows(exceptionType.java) {
                runTest {
                    block(createDeltakerClient(expectedUrl, statusCode))
                }
            }
            thrown.message shouldStartWith expectedErrorMessage
        }

        private fun <T> runHappyPathTest(
            expectedUrl: String,
            expectedResponse: T,
            block: suspend (AmtDeltakerClient) -> T,
        ) = runTest {
            val deltakerClient = createDeltakerClient(
                expectedUrl = expectedUrl,
                statusCode = HttpStatusCode.OK,
                responseBody = expectedResponse,
            )

            if (expectedResponse == null) {
                shouldNotThrowAny { block(deltakerClient) }
            } else {
                block(deltakerClient) shouldBe expectedResponse
            }
        }

        private fun createDeltakerClient(
            expectedUrl: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: Any? = null,
            personIdentCache: CountingCache<UUID, String>? = null,
        ) = AmtDeltakerClient(
            baseUrl = DELTAKER_BASE_URL,
            scope = "scope",
            httpClient = createMockHttpClient(
                expectedUrl = expectedUrl,
                responseBody = responseBody,
                statusCode = statusCode,
            ),
            azureAdTokenClient = mockAzureAdClient(),
            personIdentCache = personIdentCache ?: CountingCache(),
        )
    }
}
