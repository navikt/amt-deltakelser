package no.nav.amt.deltaker.bff.innbygger.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.tokenXToken
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.writePolymorphicListAsString
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class InnbyggerApiTest : IntegrationTestBase() {
    @BeforeEach
    fun setup() {
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(any()) } returns PersonIdentResponse("123").personident
    }

    @Test
    fun `skal teste tilgangskontroll - har ikke tilgang - returnerer 403`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )
        every { deltakerRepository.get(any()) } returns Result.success(TestData.lagDeltaker())

        withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${UUID.randomUUID()}") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
            httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast") { noBodyRequest() }.status shouldBe
                HttpStatusCode.Forbidden
            httpClient.get("/innbygger/${UUID.randomUUID()}/historikk") { noBodyRequest() }.status shouldBe
                HttpStatusCode.Forbidden
        }
    }

    @Test
    fun `skal teste tilgangskontroll - mangler token - returnerer 401`() {
        every { deltakerRepository.get(any()) } returns Result.success(TestData.lagDeltaker())

        withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
            httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast").status shouldBe HttpStatusCode.Unauthorized
            httpClient.get("/innbygger/${UUID.randomUUID()}/historikk").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get deltaker - returnerer 200 og deltaker`() = runTest {
        // Arrange
        val deltaker = TestData.lagDeltakerResponse()

        coEvery { deltakerService.oppdaterSistBesokt(deltaker.id) } just Runs
        coEvery { amtDeltakerClient.getDeltaker(any()) } returns deltaker

        // Act
        val httpResponse = withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${deltaker.id}") { noBodyRequest() }
        }

        // Assert
        httpResponse.status shouldBe HttpStatusCode.OK
        httpResponse.bodyAsText() shouldBe objectMapper.writeValueAsString(
            ModelMapper
                .toDeltaker(deltaker)
                .let {
                    InnbyggerDeltakerResponse.fromModel(
                        deltaker = it,
                        utflatetKodeverk = null,
                    )
                },
        )
    }

    @Test
    fun `get id - deltaker finnes ikke - returnerer 404`() {
        every { deltakerRepository.get(any()) } returns Result.failure(NoSuchElementException())
        coEvery { amtDeltakerClient.getDeltaker(any()) } throws NoSuchElementException()
        withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${UUID.randomUUID()}") { noBodyRequest() }.status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `godkjenn-utkast - deltaker finnes ikke - returnerer 404`() {
        coEvery { paameldingClient.innbyggerGodkjennUtkast(any()) } throws NoSuchElementException()
        coEvery { amtDeltakerClient.getDeltaker(any()) } throws NoSuchElementException()

        withTestApplicationContext { httpClient ->
            httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast") { noBodyRequest() }.status shouldBe
                HttpStatusCode.NotFound
        }
    }

    @Test
    fun `godkjenn-utkast - har tilgang, toggle på - returnerer korrekt mappet respons`() {
        val deltakerResponse = TestData.lagDeltakerResponse()
        val deltakerId = deltakerResponse.id

        coEvery { paameldingClient.innbyggerGodkjennUtkast(deltakerId) } returns deltakerResponse
        coEvery { amtDeltakerClient.getDeltaker(deltakerId) } returns deltakerResponse

        val expected = InnbyggerDeltakerResponse.fromModel(
            deltaker = ModelMapper.toDeltaker(deltakerResponse),
            utflatetKodeverk = null,
        )

        withTestApplicationContext { httpClient ->
            val httpResponse = httpClient.post("/innbygger/$deltakerId/godkjenn-utkast") { noBodyRequest() }

            httpResponse.status shouldBe HttpStatusCode.OK
            httpResponse.bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
        }
    }

    @Test
    fun `getHistorikk - deltaker finnes, har tilgang - returnerer historikk`() {
        val deltaker = TestData.leggTilHistorikk(TestData.lagDeltaker(), 2, 2, 1)
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        val historikk = deltaker.historikk
        val ansatte = TestData.lagNavAnsatteForHistorikk(historikk).associateBy { it.id }
        val enheter = TestData.lagNavEnheterForHistorikk(historikk).associateBy { it.id }

        val deltakerResponse = TestData.lagDeltakerResponse(id = deltaker.id)
        val arrangornavn = deltakerResponse.gjennomforing.arrangor!!.navn
        val oppstartstype = deltakerResponse.gjennomforing.oppstart

        coEvery { amtDeltakerClient.getDeltakerHistorikkData(deltaker.id) } returns DeltakerHistorikkDataResponse(
            historikk = historikk,
            arrangornavn = arrangornavn,
            oppstartstype = oppstartstype,
            pameldingstype = null,
            ansatte = ansatte,
            enheter = enheter,
        )

        withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${deltaker.id}/historikk") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writePolymorphicListAsString(
                    DeltakerHistorikkResponse.fromModels(
                        models = historikk,
                        arrangornavn = arrangornavn,
                        oppstartstype = oppstartstype,
                        pameldingstype = null,
                        enheter = enheter,
                        ansatte = ansatte,
                    ),
                )
            }
        }
    }

    companion object {
        private fun HttpRequestBuilder.noBodyRequest() = bearerAuth("${tokenXToken("personident", Environment())}")
    }
}
