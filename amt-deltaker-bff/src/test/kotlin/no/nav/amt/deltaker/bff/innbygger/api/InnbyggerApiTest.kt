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
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.tokenXToken
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
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
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(any()) } returns PersonIdentResponse("123")
    }

    @Test
    fun `skal teste tilgangskontroll - har ikke tilgang - returnerer 403`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )
        every { deltakerRepository.get(any()) } returns Result.success(TestData.lagDeltaker())

        withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${UUID.randomUUID()}") { noBodyRequest() }.status shouldBe HttpStatusCode.Companion.Forbidden
            httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast") { noBodyRequest() }.status shouldBe
                HttpStatusCode.Companion.Forbidden
            httpClient.get("/innbygger/${UUID.randomUUID()}/historikk") { noBodyRequest() }.status shouldBe
                HttpStatusCode.Companion.Forbidden
        }
    }

    @Test
    fun `skal teste tilgangskontroll - mangler token - returnerer 401`() {
        every { deltakerRepository.get(any()) } returns Result.success(TestData.lagDeltaker())

        withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Companion.Unauthorized
            httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast").status shouldBe HttpStatusCode.Companion.Unauthorized
            httpClient.get("/innbygger/${UUID.randomUUID()}/historikk").status shouldBe HttpStatusCode.Companion.Unauthorized
        }
    }

    @Test
    fun `get deltaker - returnerer 200 og deltaker`() = runTest {
        // Arrange
        val deltaker = TestData.lagDeltakerResponse()

        coEvery { deltakerService.oppdaterSistBesokt(deltaker.id) } just Runs
        coEvery { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns true
        coEvery { amtDeltakerClient.getDeltaker(any()) } returns deltaker

        // Act
        val httpResponse = withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${deltaker.id}") { noBodyRequest() }
        }

        // Assert
        httpResponse.status shouldBe HttpStatusCode.Companion.OK
        httpResponse.bodyAsText() shouldBe objectMapper.writeValueAsString(
            ModelMapper.Companion
                .toDeltaker(deltaker)
                .let { InnbyggerDeltakerResponse.fromModel(it) },
        )
    }

    @Test
    fun `get id - deltaker finnes ikke - returnerer 404`() {
        every { deltakerRepository.get(any()) } returns Result.failure(NoSuchElementException())
        coEvery { amtDeltakerClient.getDeltaker(any()) } throws NoSuchElementException()
        withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${UUID.randomUUID()}") { noBodyRequest() }.status shouldBe HttpStatusCode.Companion.NotFound
        }
    }

    @Test
    fun `godkjenn-utkast - deltaker finnes ikke - returnerer 404`() {
        coEvery { paameldingClient.innbyggerGodkjennUtkast(any()) } returns mockk()
        coEvery { amtDeltakerClient.getDeltaker(any()) } throws NoSuchElementException()

        withTestApplicationContext { httpClient ->
            httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast") { noBodyRequest() }.status shouldBe
                HttpStatusCode.Companion.NotFound
        }
    }

    @Test
    fun `godkjenn-utkast - har tilgang, toggle på - returnerer korrekt mappet respons`() {
        val deltakerResponse = TestData.lagDeltakerResponse()
        val deltakerId = deltakerResponse.id

        coEvery { paameldingClient.innbyggerGodkjennUtkast(deltakerId) } returns mockk()
        coEvery { amtDeltakerClient.getDeltaker(deltakerId) } returns deltakerResponse

        val expected = InnbyggerDeltakerResponse.fromModel(ModelMapper.Companion.toDeltaker(deltakerResponse))

        withTestApplicationContext { httpClient ->
            val httpResponse = httpClient.post("/innbygger/$deltakerId/godkjenn-utkast") { noBodyRequest() }

            httpResponse.status shouldBe HttpStatusCode.Companion.OK
            httpResponse.bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
        }
    }

    @Test
    fun `getHistorikk - deltaker finnes, har tilgang - returnerer historikk`() {
        val deltaker = TestData.leggTilHistorikk(TestData.lagDeltaker(), 2, 2, 1)
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        val historikk = deltaker.getDeltakerHistorikkForVisning()
        val ansatte = TestData.lagNavAnsatteForHistorikk(historikk).associateBy { it.id }
        val enheter = TestData.lagNavEnheterForHistorikk(historikk).associateBy { it.id }

        val deltakerResponse = TestData.lagDeltakerResponse(id = deltaker.id)
        val arrangornavn = deltakerResponse.gjennomforing.arrangor!!.navn
        val oppstartstype = deltakerResponse.gjennomforing.oppstart

        every { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns true
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
                status shouldBe HttpStatusCode.Companion.OK
                bodyAsText() shouldBe objectMapper.writePolymorphicListAsString(
                    DeltakerHistorikkResponse.Companion.fromModels(
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

    private fun setupMocks(
        deltaker: Deltaker,
        oppdatertDeltaker: Deltaker? = null,
        forslag: List<Forslag> = emptyList(),
    ): Pair<Map<UUID, NavAnsatt>, NavEnhet?> {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        every { forslagRepository.getForDeltaker(deltaker.id) } returns forslag

        return if (oppdatertDeltaker == null) {
            mockAnsatteOgEnhetForDeltaker(deltaker)
        } else {
            mockAnsatteOgEnhetForDeltaker(oppdatertDeltaker)
        }
    }

    private fun mockAnsatteOgEnhetForDeltaker(deltaker: Deltaker): Pair<Map<UUID, NavAnsatt>, NavEnhet?> {
        val ansatte = TestData.lagNavAnsatteForDeltaker(deltaker).associateBy { it.id }
        val enhet = deltaker.vedtaksinformasjon?.let {
            no.nav.amt.lib.testing.utils.TestData
                .lagNavEnhet(id = it.sistEndretAvEnhet)
        }
        val enheter = TestData.lagNavEnheterForHistorikk(deltaker.historikk).associateBy { it.id }

        every { navAnsattService.hentAnsatteForDeltaker(deltaker) } returns ansatte
        enhet?.let { every { navEnhetService.hentEnhet(it.id) } returns it }
        coEvery { navEnhetService.hentEnheterForHistorikk(any()) } returns enheter

        return Pair(ansatte, enhet)
    }

    companion object {
        private fun HttpRequestBuilder.noBodyRequest() = bearerAuth("${tokenXToken("personident", Environment())}")
    }
}
