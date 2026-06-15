package no.nav.amt.deltaker.bff.clients

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorClient
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.AvslagRequest
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerOld
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakskoordinatorDeltakerResponse
import no.nav.amt.internapi.deltaker.response.PaginatedResult
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorDeltakerIListeResponse
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.utils.ClientTestUtils.createMockHttpClient
import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import kotlin.reflect.KClass

class TiltaksKoordinatorClientTest {
    @Nested
    inner class GetGjennomforing {
        val expectedUrl = "$CLIENT_BASE_URL/gjennomforing/$gjennomforingId"
        val expectedErrorMessage = "Fant ikke gjennomforing $gjennomforingId i amt-deltaker."
        val getGjennomforingLambda: suspend (TiltakskoordinatorClient) -> Any =
            { client -> client.getGjennomforing(gjennomforingId) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                expectedExceptionType,
                statusCode,
                expectedUrl,
                expectedErrorMessage,
                getGjennomforingLambda,
                expectedMethod = HttpMethod.Get,
            )
        }
    }

    @Nested
    inner class TildelPlass {
        val expectedUrl = "$CLIENT_BASE_URL/tiltakskoordinator/deltakere/tildel-plass"
        val expectedErrorMessage = "Kunne ikke tildele plass i amt-deltaker."
        val tildelPlassLambda: suspend (TiltakskoordinatorClient) -> List<DeltakerOppdateringResponse> =
            { client ->
                client.tildelPlass(
                    gjennomforingId = deltakerInTest.deltakerliste.id,
                    deltakerIder = listOf(deltakerInTest.id),
                    endretAv = "~endretAv~",
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, tildelPlassLambda)
        }

        @Test
        fun `skal returnere liste med TiltakskoordinatorDeltakerResponse`() {
            runHappyPathTest(
                expectedUrl,
                listOf(
                    DeltakerOppdateringResponse(deltaker = lagTiltakskoordinatorDeltakerResponse(id = deltakerInTest.id), feilkode = null),
                ),
                tildelPlassLambda,
            )
        }
    }

    @Nested
    inner class SettPaaVenteliste {
        val expectedUrl = "$CLIENT_BASE_URL/tiltakskoordinator/deltakere/sett-paa-venteliste"
        val expectedErrorMessage = "Kunne ikke sette på venteliste i amt-deltaker."
        val settPaaVentelisteLambda: suspend (TiltakskoordinatorClient) -> List<DeltakerOppdateringResponse> =
            { client ->
                client.settPaaVenteliste(
                    gjennomforingId = deltakerInTest.deltakerliste.id,
                    deltakerIder = listOf(deltakerInTest.id),
                    endretAv = "~endretAv~",
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, settPaaVentelisteLambda)
        }

        @Test
        fun `skal returnere liste med TiltakskoordinatorDeltakerResponse`() {
            runHappyPathTest(
                expectedUrl,
                listOf(
                    DeltakerOppdateringResponse(deltaker = lagTiltakskoordinatorDeltakerResponse(id = deltakerInTest.id), feilkode = null),
                ),
                settPaaVentelisteLambda,
            )
        }
    }

    @Nested
    inner class GetDeltakereForGjennomforing {
        val expectedUrl = "$CLIENT_BASE_URL/tiltakskoordinator/deltakere/$gjennomforingId"
        val expectedErrorMessage = "Fant ikke gjennomforing $gjennomforingId i amt-deltaker."
        val getDeltakereForGjennomforingLambda: suspend (
            TiltakskoordinatorClient,
        ) -> PaginatedResult<TiltakskoordinatorDeltakerIListeResponse> =
            { client -> client.getDeltakereForGjennomforing(deltakereRequest) }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(
                expectedExceptionType,
                statusCode,
                expectedUrl,
                expectedErrorMessage,
                getDeltakereForGjennomforingLambda,
                expectedMethod = HttpMethod.Post,
            )
        }

        @Test
        fun `skal returnere deltakere for gjennomforing`() {
            runHappyPathTest(
                expectedUrl,
                lagPaginatedTiltakskoordinatorDeltakere(),
                getDeltakereForGjennomforingLambda,
                expectedMethod = HttpMethod.Post,
            )
        }
    }

    @Nested
    inner class DelMedArrangor {
        val expectedUrl = "$CLIENT_BASE_URL/tiltakskoordinator/deltakere/del-med-arrangor"
        val expectedErrorMessage = "Kunne ikke dele-med-arrangor i amt-deltaker."
        val delMedArrangorLambda: suspend (TiltakskoordinatorClient) -> List<DeltakerOppdateringResponse> =
            { client ->
                client.delMedArrangor(
                    gjennomforingId = deltakerInTest.deltakerliste.id,
                    deltakerIder = listOf(deltakerInTest.id),
                    endretAv = "~endretAv~",
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, delMedArrangorLambda)
        }

        @Test
        fun `skal returnere liste med TiltakskoordinatorDeltakerResponse`() {
            runHappyPathTest(
                expectedUrl,
                listOf(
                    DeltakerOppdateringResponse(deltaker = lagTiltakskoordinatorDeltakerResponse(id = deltakerInTest.id), feilkode = null),
                ),
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
        val giAvslagLambda: suspend (TiltakskoordinatorClient) -> DeltakerOppdateringResponse =
            { client ->
                client.giAvslag(
                    gjennomforingId = deltakerInTest.deltakerliste.id,
                    avslagRequest = avslagRequest,
                    endretAv = "~endretAv~",
                )
            }

        @ParameterizedTest
        @MethodSource("no.nav.amt.lib.testing.utils.ClientTestUtils#failureCases")
        fun `skal kaste riktig exception ved feilrespons`(testCase: Pair<HttpStatusCode, KClass<out Throwable>>) {
            val (statusCode, expectedExceptionType) = testCase
            runFailureTest(expectedExceptionType, statusCode, expectedUrl, expectedErrorMessage, giAvslagLambda)
        }

        @Test
        fun `skal returnere TiltakskoordinatorDeltakerResponse`() {
            runHappyPathTest(
                expectedUrl,
                DeltakerOppdateringResponse(deltaker = lagTiltakskoordinatorDeltakerResponse(id = deltakerInTest.id), feilkode = null),
                giAvslagLambda,
            )
        }
    }

    companion object {
        private const val CLIENT_BASE_URL = "http://amt-tiltakskoordinator"
        private val gjennomforingId = UUID.randomUUID()
        private val deltakereRequest = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = gjennomforingId,
        )
        private val deltakerInTest = lagDeltakerOld()

        private fun lagPaginatedTiltakskoordinatorDeltakere() = PaginatedResult(
            totalCount = 1,
            pageSize = 50,
            data = listOf(lagTiltakskoordinatorDeltakerResponse()),
        )

        private fun runFailureTest(
            exceptionType: KClass<out Throwable>,
            statusCode: HttpStatusCode,
            expectedUrl: String,
            expectedError: String,
            block: suspend (TiltakskoordinatorClient) -> Any,
            expectedMethod: HttpMethod? = null,
        ) {
            val thrown = Assertions.assertThrows(exceptionType.java) {
                runTest {
                    block(createTiltaksKoordinatorClient(expectedUrl, statusCode, expectedMethod = expectedMethod))
                }
            }
            thrown.message shouldStartWith expectedError
        }

        private fun <T> runHappyPathTest(
            expectedUrl: String,
            expectedResponse: T,
            block: suspend (TiltakskoordinatorClient) -> T,
            expectedMethod: HttpMethod? = null,
        ) = runTest {
            val deltakerClient = createTiltaksKoordinatorClient(expectedUrl, HttpStatusCode.OK, expectedResponse, expectedMethod)

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
            expectedMethod: HttpMethod? = null,
        ) = TiltakskoordinatorClient(
            baseUrl = CLIENT_BASE_URL,
            scope = "scope",
            httpClient = createMockHttpClient(expectedUrl, responseBody, statusCode, expectedMethod = expectedMethod),
            azureAdTokenClient = mockAzureAdClient(),
        )
    }
}
