package no.nav.amt.deltaker.bff.innbygger

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
import no.nav.amt.deltaker.bff.apiclients.ModelMapper
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.innbygger.InnbyggerTestUtils.fattVedtak
import no.nav.amt.deltaker.bff.innbygger.model.InnbyggerDeltakerResponse
import no.nav.amt.deltaker.bff.innbygger.model.toInnbyggerDeltakerResponse
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerResponse
import no.nav.amt.deltaker.bff.utils.data.TestData.lagForslag
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavAnsatteForDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavAnsatteForHistorikk
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavEnheterForHistorikk
import no.nav.amt.deltaker.bff.utils.data.TestData.leggTilHistorikk
import no.nav.amt.deltaker.bff.utils.tokenXToken
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.writePolymorphicListAsString
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class InnbyggerApiTest : IntegrationTestBase() {
    @BeforeEach
    fun setup() {
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(any()) } returns PersonIdentResponse("123")
        coEvery { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns true
    }

    @Nested
    inner class ToggleAvTester {
        // Hele klassen kan slettes når toggle ikke brukes mer(samme tester i ytre klasse)
        @BeforeEach
        fun setup() {
            coEvery { amtDeltakerClient.getPersonidentForDeltaker(any()) } returns PersonIdentResponse("123")
            coEvery { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns false
        }

        @Test
        fun `skal teste tilgangskontroll - har ikke tilgang - returnerer 403`() {
            every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
                null,
                Decision.Deny("Ikke tilgang", ""),
            )
            every { deltakerRepository.get(any()) } returns Result.success(lagDeltaker())

            withTestApplicationContext { httpClient ->
                httpClient.get("/innbygger/${UUID.randomUUID()}") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
                httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast") { noBodyRequest() }.status shouldBe
                    HttpStatusCode.Forbidden
                httpClient.get("/innbygger/${UUID.randomUUID()}/historikk") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
            }
        }

        @Test
        fun `skal teste tilgangskontroll - mangler token - returnerer 401`() {
            every { deltakerRepository.get(any()) } returns Result.success(lagDeltaker())

            withTestApplicationContext { httpClient ->
                httpClient.get("/innbygger/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
                httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast").status shouldBe HttpStatusCode.Unauthorized
                httpClient.get("/innbygger/${UUID.randomUUID()}/historikk").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `get deltaker - toggle er av - returnerer 200 og deltaker`() = runTest {
            // Arrange
            val deltaker = lagDeltaker()
            val forslag = lagForslag(deltakerId = deltaker.id)
            val (ansatte, enhet) = setupMocks(deltaker, forslag = listOf(forslag))

            coEvery { deltakerService.oppdaterSistBesokt(deltaker.id) } just Runs

            // Act
            val httpResponse = withTestApplicationContext { httpClient ->
                httpClient.get("/innbygger/${deltaker.id}") { noBodyRequest() }
            }

            // Assert
            httpResponse.status shouldBe HttpStatusCode.OK
            httpResponse.bodyAsText() shouldBe objectMapper.writeValueAsString(
                deltaker.toInnbyggerDeltakerResponse(
                    ansatte = ansatte,
                    vedtakSistEndretAvEnhet = enhet,
                    forslag = listOf(forslag),
                ),
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
            every { deltakerRepository.get(any()) } returns Result.failure(NoSuchElementException())

            withTestApplicationContext { httpClient ->
                httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast") { noBodyRequest() }.status shouldBe
                    HttpStatusCode.NotFound
            }
        }

        @Test
        fun `godkjenn-utkast - deltaker har tilgang, toggle er av - fatter vedtak`() {
            val deltaker = InnbyggerTestUtils.deltakerMedIkkeFattetVedtak()
            val deltakerMedFattetVedtak = deltaker.fattVedtak()

            coEvery { innbyggerService.godkjennUtkast(deltaker) } returns deltakerMedFattetVedtak
            val (ansatte, enhet) = setupMocks(deltaker, deltakerMedFattetVedtak)

            withTestApplicationContext { httpClient ->
                val httpResponse = httpClient.post("/innbygger/${deltaker.id}/godkjenn-utkast") { noBodyRequest() }

                httpResponse.status shouldBe HttpStatusCode.OK
                httpResponse.bodyAsText() shouldBe objectMapper.writeValueAsString(
                    deltakerMedFattetVedtak.toInnbyggerDeltakerResponse(
                        ansatte = ansatte,
                        vedtakSistEndretAvEnhet = enhet,
                        forslag = emptyList(),
                    ),
                )
            }
        }

        @Test
        fun `getHistorikk - returnerer lokal historikk`() {
            val deltaker = leggTilHistorikk(lagDeltaker(), 2, 2, 1)
            every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            val historikk = deltaker.getDeltakerHistorikkForVisning()
            val ansatte = lagNavAnsatteForHistorikk(historikk).associateBy { it.id }
            val enheter = lagNavEnheterForHistorikk(historikk).associateBy { it.id }

            every { navAnsattService.hentAnsatteForHistorikk(historikk) } returns ansatte
            coEvery { navEnhetService.hentEnheterForHistorikk(historikk) } returns enheter

            withTestApplicationContext { httpClient ->
                httpClient.get("/innbygger/${deltaker.id}/historikk") { noBodyRequest() }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writePolymorphicListAsString(
                        DeltakerHistorikkResponse.fromModels(
                            models = historikk,
                            arrangornavn = deltaker.deltakerliste.arrangor.getArrangorNavn(),
                            oppstartstype = deltaker.deltakerliste.oppstart,
                            pameldingstype = deltaker.deltakerliste.pameldingstype,
                            enheter = enheter,
                            ansatte = ansatte,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `skal teste tilgangskontroll - har ikke tilgang - returnerer 403`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )
        every { deltakerRepository.get(any()) } returns Result.success(lagDeltaker())

        withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${UUID.randomUUID()}") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
            httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
            httpClient.get("/innbygger/${UUID.randomUUID()}/historikk") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
        }
    }

    @Test
    fun `skal teste tilgangskontroll - mangler token - returnerer 401`() {
        every { deltakerRepository.get(any()) } returns Result.success(lagDeltaker())

        withTestApplicationContext { httpClient ->
            httpClient.get("/innbygger/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
            httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast").status shouldBe HttpStatusCode.Unauthorized
            httpClient.get("/innbygger/${UUID.randomUUID()}/historikk").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get deltaker - returnerer 200 og deltaker`() = runTest {
        // Arrange
        val deltaker = lagDeltakerResponse()

        coEvery { deltakerService.oppdaterSistBesokt(deltaker.id) } just Runs
        coEvery { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns true
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
                .let { InnbyggerDeltakerResponse.fromModel(it) },
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
        coEvery { paameldingClient.innbyggerGodkjennUtkast(any()) } returns mockk()
        coEvery { amtDeltakerClient.getDeltaker(any()) } throws NoSuchElementException()

        withTestApplicationContext { httpClient ->
            httpClient.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast") { noBodyRequest() }.status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `godkjenn-utkast - har tilgang, toggle på - returnerer korrekt mappet respons`() {
        val deltakerResponse = lagDeltakerResponse()
        val deltakerId = deltakerResponse.id

        coEvery { paameldingClient.innbyggerGodkjennUtkast(deltakerId) } returns mockk()
        coEvery { amtDeltakerClient.getDeltaker(deltakerId) } returns deltakerResponse

        val expected = InnbyggerDeltakerResponse.fromModel(ModelMapper.toDeltaker(deltakerResponse))

        withTestApplicationContext { httpClient ->
            val httpResponse = httpClient.post("/innbygger/$deltakerId/godkjenn-utkast") { noBodyRequest() }

            httpResponse.status shouldBe HttpStatusCode.OK
            httpResponse.bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
        }
    }

    @Test
    fun `getHistorikk - deltaker finnes, har tilgang - returnerer historikk`() {
        val deltaker = leggTilHistorikk(lagDeltaker(), 2, 2, 1)
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        val historikk = deltaker.getDeltakerHistorikkForVisning()
        val ansatte = lagNavAnsatteForHistorikk(historikk).associateBy { it.id }
        val enheter = lagNavEnheterForHistorikk(historikk).associateBy { it.id }

        val deltakerResponse = lagDeltakerResponse(id = deltaker.id)
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
        val ansatte = lagNavAnsatteForDeltaker(deltaker).associateBy { it.id }
        val enhet = deltaker.vedtaksinformasjon?.let { lagNavEnhet(id = it.sistEndretAvEnhet) }
        val enheter = lagNavEnheterForHistorikk(deltaker.historikk).associateBy { it.id }

        every { navAnsattService.hentAnsatteForDeltaker(deltaker) } returns ansatte
        enhet?.let { every { navEnhetService.hentEnhet(it.id) } returns it }
        coEvery { navEnhetService.hentEnheterForHistorikk(any()) } returns enheter

        return Pair(ansatte, enhet)
    }

    companion object {
        private fun HttpRequestBuilder.noBodyRequest() = bearerAuth("${tokenXToken("personident", Environment())}")
    }
}
