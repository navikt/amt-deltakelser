package no.nav.amt.deltaker.bff.innbygger

import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.bff.application.plugins.configureAuthentication
import no.nav.amt.deltaker.bff.application.plugins.configureRouting
import no.nav.amt.deltaker.bff.application.plugins.configureSerialization
import no.nav.amt.deltaker.bff.application.plugins.writePolymorphicListAsString
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorsDeltakerlisteProducer
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.api.model.getArrangorNavn
import no.nav.amt.deltaker.bff.deltaker.api.model.toResponse
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.innbygger.model.toInnbyggerDeltakerResponse
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.tiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.bff.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.tokenXToken
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.objectMapper
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class InnbyggerApiTest {
    private val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val deltakerService = mockk<DeltakerService>(relaxUnitFun = true)
    private val pameldingService = mockk<PameldingService>()
    private val navAnsattService = mockk<NavAnsattService>()
    private val navEnhetService = mockk<NavEnhetService>()
    private val forslagRepository = mockk<ForslagRepository>()
    private val forslagService = mockk<ForslagService>()
    private val innbyggerService = mockk<InnbyggerService>()
    private val amtDistribusjonClient = mockk<AmtDistribusjonClient>()
    private val tiltakskoordinatorTilgangRepository = mockk<TiltakskoordinatorTilgangRepository>()
    private val tiltakskoordinatorsDeltakerlisteProducer = mockk<TiltakskoordinatorsDeltakerlisteProducer>()
    private val tiltakskoordinatorService = mockk<TiltakskoordinatorService>()
    private val deltakerlisteService = mockk<DeltakerlisteService>()
    private val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient,
        navAnsattService,
        tiltakskoordinatorTilgangRepository,
        tiltakskoordinatorsDeltakerlisteProducer,
        tiltakskoordinatorService,
        deltakerlisteService,
    )

    @BeforeEach
    fun setup() = configureEnvForAuthentication()

    @Test
    fun `skal teste tilgangskontroll - har ikke tilgang - returnerer 403`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )
        every { deltakerRepository.get(any()) } returns Result.success(TestData.lagDeltaker())

        setUpTestApplication()
        client.get("/innbygger/${UUID.randomUUID()}") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
        client.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
        client.get("/innbygger/${UUID.randomUUID()}/historikk") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `skal teste tilgangskontroll - mangler token - returnerer 401`() = testApplication {
        every { deltakerRepository.get(any()) } returns Result.success(TestData.lagDeltaker())

        setUpTestApplication()
        client.get("/innbygger/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
        client.post("/innbygger/${UUID.randomUUID()}/godkjenn-utkast").status shouldBe HttpStatusCode.Unauthorized
        client.get("/innbygger/${UUID.randomUUID()}/historikk").status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `get id - innbygger har tilgang - returnerer 200 og deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker()
        val forslag = TestData.lagForslag(deltakerId = deltaker.id)
        val (ansatte, enhet) = setupMocks(deltaker, forslag = listOf(forslag))

        val res = client.get("/innbygger/${deltaker.id}") { noBodyRequest() }
        res.status shouldBe HttpStatusCode.OK
        res.bodyAsText() shouldBe objectMapper.writeValueAsString(
            deltaker.toInnbyggerDeltakerResponse(
                ansatte,
                enhet,
                listOf(forslag),
            ),
        )
    }

    @Test
    fun `get id - deltaker finnes ikke - returnerer 404`() = testApplication {
        every { deltakerRepository.get(any()) } returns Result.failure(NoSuchElementException())

        setUpTestApplication()
        val res = client.get("/innbygger/${UUID.randomUUID()}") { noBodyRequest() }
        res.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `godkjenn-utkast - deltaker har feil status - feiler`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        setupMocks(deltaker)

        val res = client.post("/innbygger/${deltaker.id}/godkjenn-utkast") { noBodyRequest() }
        res.status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun `godkjenn-utkast - deltaker finnes ikke - returnerer 404`() = testApplication {
        every { deltakerRepository.get(any()) } returns Result.failure(NoSuchElementException())

        setUpTestApplication()
        val res = client.get("/innbygger/${UUID.randomUUID()}/godkjenn-utkast") { noBodyRequest() }
        res.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `godkjenn-utkast - deltaker har tilgang - fatter vedtak`() = testApplication {
        setUpTestApplication()
        val deltaker = deltakerMedIkkeFattetVedtak()
        val deltakerMedFattetVedak = deltaker.fattVedtak()

        coEvery { innbyggerService.godkjennUtkast(deltaker) } returns deltakerMedFattetVedak
        val (ansatte, enhet) = setupMocks(deltaker, deltakerMedFattetVedak)

        val res = client.post("/innbygger/${deltaker.id}/godkjenn-utkast") { noBodyRequest() }
        res.status shouldBe HttpStatusCode.OK
        res.bodyAsText() shouldBe objectMapper.writeValueAsString(
            deltakerMedFattetVedak.toInnbyggerDeltakerResponse(
                ansatte,
                enhet,
                emptyList(),
            ),
        )
    }

    @Test
    fun `getHistorikk - deltaker finnes, har tilgang - returnerer historikk`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker().let { TestData.leggTilHistorikk(it, 2, 2, 1) }
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

        val historikk = deltaker.getDeltakerHistorikkForVisning()
        val ansatte = TestData.lagNavAnsatteForHistorikk(historikk).associateBy { it.id }
        val enheter = TestData.lagNavEnheterForHistorikk(historikk).associateBy { it.id }

        every { navAnsattService.hentAnsatteForHistorikk(historikk) } returns ansatte
        coEvery { navEnhetService.hentEnheterForHistorikk(historikk) } returns enheter
        client.get("/innbygger/${deltaker.id}/historikk") { noBodyRequest() }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writePolymorphicListAsString(
                historikk.toResponse(
                    ansatte,
                    deltaker.deltakerliste.arrangor.getArrangorNavn(),
                    enheter,
                    deltaker.deltakerliste.oppstart,
                ),
            )
        }
    }

    private fun HttpRequestBuilder.noBodyRequest() = bearerAuth("${tokenXToken("personident", Environment())}")

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                tilgangskontrollService = tilgangskontrollService,
                deltakerService = deltakerService,
                pameldingService = pameldingService,
                navAnsattService = navAnsattService,
                navEnhetService = navEnhetService,
                innbyggerService = innbyggerService,
                forslagRepository = forslagRepository,
                forslagService = forslagService,
                amtDistribusjonClient = amtDistribusjonClient,
                sporbarhetsloggService = mockk(),
                deltakerRepository = deltakerRepository,
                deltakerlisteService = mockk(),
                unleash = mockk(),
                sporbarhetOgTilgangskontrollSvc = mockk(),
                tiltakskoordinatorService = mockk(),
                tiltakskoordinatorTilgangRepository = mockk(),
                ulestHendelseService = mockk(),
                testdataService = mockk(),
            )
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
        val enhet = deltaker.vedtaksinformasjon?.let { TestData.lagNavEnhet(id = it.sistEndretAvEnhet) }
        val enheter = TestData.lagNavEnheterForHistorikk(deltaker.historikk).associateBy { it.id }

        every { navAnsattService.hentAnsatteForDeltaker(deltaker) } returns ansatte
        enhet?.let { every { navEnhetService.hentEnhet(it.id) } returns it }
        coEvery { navEnhetService.hentEnheterForHistorikk(any()) } returns enheter

        return Pair(ansatte, enhet)
    }
}

fun deltakerMedIkkeFattetVedtak(): Deltaker {
    val deltaker = TestData.lagDeltaker(
        status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        historikk = false,
    )
    val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker, fattet = null)

    return deltaker.copy(historikk = listOf(DeltakerHistorikk.Vedtak(vedtak)))
}

fun Deltaker.fattVedtak(): Deltaker {
    val vedtak = this.ikkeFattetVedtak!!

    return this.copy(
        historikk = this.historikk
            .filter { it.id != vedtak.id }
            .plus(
                DeltakerHistorikk.Vedtak(
                    vedtak.copy(
                        fattet = LocalDateTime.now(),
                        sistEndret = LocalDateTime.now(),
                    ),
                ),
            ),
    )
}

private val DeltakerHistorikk.id
    get() = when (this) {
        is DeltakerHistorikk.Endring -> endring.id
        is DeltakerHistorikk.Vedtak -> vedtak.id
        is DeltakerHistorikk.Forslag -> forslag.id
        is DeltakerHistorikk.EndringFraArrangor -> endringFraArrangor.id
        is DeltakerHistorikk.ImportertFraArena -> importertFraArena.deltakerId
        is DeltakerHistorikk.VurderingFraArrangor -> data.id
        is DeltakerHistorikk.EndringFraTiltakskoordinator -> endringFraTiltakskoordinator.id
        is DeltakerHistorikk.InnsokPaaFellesOppstart -> data.id
    }
