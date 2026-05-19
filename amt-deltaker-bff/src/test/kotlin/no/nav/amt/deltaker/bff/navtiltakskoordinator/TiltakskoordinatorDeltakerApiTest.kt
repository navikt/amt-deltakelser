package no.nav.amt.deltaker.bff.navtiltakskoordinator

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerDetaljerResponse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseMapper
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
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
        fun `skal returnere DeltakerDetaljerResponse - toggle er på`(harTilgangTilBruker: Boolean) {
            val deltaker = lagDeltakerResponse()

            coEvery { amtDeltakerClient.getDeltaker(any()) } returns deltaker

            every { ulestHendelseRepository.getForDeltaker(any()) } returns emptyList()

            coEvery {
                tiltakskoordinatorTilgangskontrollService.kontrollerTilgangTilBruker(
                    navIdent = any(),
                    navAnsattAzureId = any(),
                    personident = any(),
                    erSkjermet = any(),
                    adressebeskyttelse = any(),
                    deltakerlisteId = any(),
                )
            } returns harTilgangTilBruker

            val responseBody = withTestApplicationContext { httpClient ->
                httpClient
                    .get(urlString) {
                        bearerAuth(bearerTokenInTest)
                    }.body<DeltakerDetaljerResponse>()
            }

            responseBody shouldBe ModelMapper
                .toDeltaker(deltaker)
                .let {
                    ResponseMapper.buildDeltakerDetaljerResponse(it, harTilgangTilBruker, emptyList())
                }
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
            coEvery { amtDeltakerClient.getDeltaker(any()) } returns deltakerResponse
            coEvery {
                tiltakskoordinatorTilgangskontrollService.kontrollerTilgangTilBruker(
                    navIdent = any(),
                    navAnsattAzureId = any(),
                    deltakerlisteId = any(),
                    personident = any(),
                    erSkjermet = any(),
                    adressebeskyttelse = any(),
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
            // Arrange
            val historikk = deltaker.getDeltakerHistorikkForVisning()

            val navAnsattMap = mapOf(navAnsatt.id to navAnsatt)
            val navEnhetMap = mapOf(navEnhet.id to navEnhet)

            val deltakerResponse = lagDeltakerResponse(id = deltaker.id)
            val arrangornavn = deltakerResponse.gjennomforing.arrangor!!.navn
            val oppstartstype = deltakerResponse.gjennomforing.oppstart

            val expectedResponse = objectMapper.writePolymorphicListAsString(
                DeltakerHistorikkResponse.fromModels(
                    models = historikk,
                    arrangornavn = arrangornavn,
                    oppstartstype = oppstartstype,
                    pameldingstype = deltakerResponse.gjennomforing.pameldingstype,
                    enheter = navEnhetMap,
                    ansatte = navAnsattMap,
                ),
            )

            coEvery { amtDeltakerClient.getDeltaker(any()) } returns deltakerResponse
            coEvery { amtDeltakerClient.getDeltakerHistorikkData(any()) } returns DeltakerHistorikkDataResponse(
                historikk = historikk,
                arrangornavn = arrangornavn,
                oppstartstype = oppstartstype,
                pameldingstype = deltakerResponse.gjennomforing.pameldingstype,
                ansatte = navAnsattMap,
                enheter = navEnhetMap,
            )
            coEvery {
                tiltakskoordinatorTilgangskontrollService.kontrollerTilgangTilBruker(
                    navIdent = any(),
                    navAnsattAzureId = any(),
                    personident = any(),
                    erSkjermet = any(),
                    adressebeskyttelse = any(),
                    deltakerlisteId = any(),
                )
            } returns true

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
        private val deltakerResponse = lagDeltakerResponse(id = deltaker.id)
        private val navAnsatt = lagNavAnsatt(id = deltaker.navBruker.navVeilederId!!)
        private val navEnhet = lagNavEnhet(id = deltaker.navBruker.navEnhetId!!)
    }
}
