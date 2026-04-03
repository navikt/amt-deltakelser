package no.nav.amt.deltaker.bff.tiltakskoordinator

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.response.DeltakerDetaljerResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.extensions.toResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.extensions.toTiltakskoordinatorsDeltaker
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.writePolymorphicListAsString
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class TiltakskoordinatorDeltakerApiTest : IntegrationTestBase() {
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
        fun `skal returnere liste med DeltakerHistorikk`() {
            val historikk = deltaker.getDeltakerHistorikkForVisning()

            val navAnsattMap = mapOf(navAnsatt.id to navAnsatt)
            val navEnhetMap = mapOf(navEnhet.id to navEnhet)

            val expectedResponse = objectMapper.writePolymorphicListAsString(
                DeltakerHistorikkResponse.fromModels(
                    models = historikk,
                    arrangornavn = deltaker.deltakerliste.arrangor.getArrangorNavn(),
                    oppstartstype = deltaker.deltakerliste.oppstart,
                    enheter = navEnhetMap,
                    ansatte = navAnsattMap,
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

        private val tiltakskoordinatorsDeltaker = deltaker
            .toTiltakskoordinatorsDeltaker(
                sisteVurdering = null,
                navEnhet = navEnhet,
                navVeileder = navAnsatt,
                feilkode = null,
                ikkeDigitalOgManglerAdresse = false,
                forslag = emptyList(),
                ulesteHendelser = emptyList(),
            )
    }
}
