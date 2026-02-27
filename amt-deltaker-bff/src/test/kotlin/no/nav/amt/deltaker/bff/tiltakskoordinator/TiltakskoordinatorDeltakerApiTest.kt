package no.nav.amt.deltaker.bff.tiltakskoordinator

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.application.plugins.writePolymorphicListAsString
import no.nav.amt.deltaker.bff.deltaker.api.model.getArrangorNavn
import no.nav.amt.deltaker.bff.deltaker.api.model.toResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.response.DeltakerDetaljerResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.extensions.toResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.extensions.toTiltakskoordinatorsDeltaker
import no.nav.amt.deltaker.bff.utils.RouteTestBase
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.bff.utils.generateJWT
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class TiltakskoordinatorDeltakerApiTest : RouteTestBase() {
    @Nested
    inner class HentDeltaker {
        private val urlString = "/tiltakskoordinator/deltaker/${UUID.randomUUID()}"

        @Test
        fun `skal returnere Unauthorized nar tilgang mangler`() {
            val response = withTestApplicationContext { httpClient -> httpClient.get(urlString) }

            response.status shouldBe HttpStatusCode.Unauthorized
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `skal returnere DeltakerDetaljerResponse`(harTilgangTilBruker: Boolean) {
            val expectedResponseBody = tiltakskoordinatorsDeltaker.toResponse(
                harTilgangTilBruker = harTilgangTilBruker,
                ulesteHendelser = emptyList(),
            )

            coEvery { tiltakskoordinatorService.getDeltaker(any()) } returns tiltakskoordinatorsDeltaker

            coEvery {
                sporbarhetOgTilgangskontrollSvc.kontrollerTilgangTilBruker(
                    navIdent = any(),
                    navAnsattAzureId = any(),
                    navBruker = any(),
                    deltakerlisteId = any(),
                )
            } returns harTilgangTilBruker

            val responseBody = withTestApplicationContext { httpClient ->
                httpClient
                    .get(urlString) {
                        bearerAuth(bearerTokenInTest)
                    }.body<DeltakerDetaljerResponse>()
            }

            responseBody shouldBe expectedResponseBody
        }
    }

    @Nested
    inner class HentDeltakerHistorikk {
        private val urlString = "/tiltakskoordinator/deltaker/${UUID.randomUUID()}/historikk"

        @Test
        fun `skal returnere Unauthorized nar tilgang mangler`() {
            val response = withTestApplicationContext { httpClient -> httpClient.get(urlString) }

            response.status shouldBe HttpStatusCode.Unauthorized
        }

        @Test
        fun `skal returnere Forbidden nar ikke tilgang til bruker`() {
            every { deltakerRepository.get(any()) } returns Result.success(deltaker)
            coEvery {
                sporbarhetOgTilgangskontrollSvc.kontrollerTilgangTilBruker(
                    navIdent = any(),
                    navAnsattAzureId = any(),
                    navBruker = any(),
                    deltakerlisteId = any(),
                )
            } returns false

            val response = withTestApplicationContext { httpClient ->
                httpClient.get(urlString) {
                    bearerAuth(bearerTokenInTest)
                }
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }

        @Test
        fun `skal returnere liste med DeltakerHistorikk`(): Unit = runBlocking {
            val historikk = deltaker.getDeltakerHistorikkForVisning()

            val navAnsattMap = mapOf(navAnsatt.id to navAnsatt)
            val navEnhetMap = mapOf(navEnhet.id to navEnhet)

            val expectedResponse = objectMapper.writePolymorphicListAsString(
                historikk.toResponse(
                    ansatte = navAnsattMap,
                    enheter = navEnhetMap,
                    arrangornavn = deltaker.deltakerliste.arrangor.getArrangorNavn(),
                    oppstartstype = deltaker.deltakerliste.oppstart,
                ),
            )

            every { deltakerRepository.get(any()) } returns Result.success(deltaker)
            coEvery {
                sporbarhetOgTilgangskontrollSvc.kontrollerTilgangTilBruker(
                    navIdent = any(),
                    navAnsattAzureId = any(),
                    navBruker = any(),
                    deltakerlisteId = any(),
                )
            } returns true

            every { navAnsattService.hentAnsatteForHistorikk(any()) } returns navAnsattMap
            coEvery { navEnhetService.hentEnheterForHistorikk(any()) } returns navEnhetMap

            val responseBody = withTestApplicationContext { httpClient ->
                httpClient
                    .get(urlString) {
                        bearerAuth(bearerTokenInTest)
                    }.bodyAsText()
            }

            responseBody shouldBe expectedResponse
        }
    }

    companion object {
        private val deltaker = lagDeltaker()
        private val navAnsatt = lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)
        private val navEnhet = lagNavEnhet(id = deltaker.navBruker.navEnhetId!!)

        val tiltakskoordinatorsDeltaker = deltaker
            .toTiltakskoordinatorsDeltaker(
                sisteVurdering = null,
                navEnhet = navEnhet,
                navVeileder = navAnsatt,
                feilkode = null,
                ikkeDigitalOgManglerAdresse = false,
                forslag = emptyList(),
                ulesteHendelser = emptyList(),
            )

        private val bearerTokenInTest = generateJWT(
            consumerClientId = "frontend-clientid",
            navAnsattAzureId = UUID.randomUUID().toString(),
            audience = "deltaker-bff",
            groups = listOf(UUID(0L, 0L).toString()),
        )
    }
}
