package no.nav.amt.deltaker.bff.veileder.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.apiclients.deltaker.AmtDeltakerClient
import no.nav.amt.deltaker.bff.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.bff.application.plugins.configureAuthentication
import no.nav.amt.deltaker.bff.application.plugins.configureRouting
import no.nav.amt.deltaker.bff.application.plugins.configureSerialization
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorsDeltakerlisteProducer
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.tiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.bff.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.veileder.api.request.InnholdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.PameldingUtenGodkjenningRequest
import no.nav.amt.deltaker.bff.veileder.api.request.UtkastRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.noBodyRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.objectMapper
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class PameldingApiTest {
    private val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val deltakerService = mockk<DeltakerService>()
    private val pameldingService = mockk<PameldingService>()
    private val navAnsattService = mockk<NavAnsattService>()
    private val navEnhetService = mockk<NavEnhetService>()
    private val forslagRepository = mockk<ForslagRepository>()
    private val forslagService = mockk<ForslagService>()
    private val amtDistribusjonClient = mockk<AmtDistribusjonClient>()
    private val amtDeltakerClient = mockk<AmtDeltakerClient>()
    private val tiltakskoordinatorTilgangRepository = mockk<TiltakskoordinatorTilgangRepository>()
    private val tiltakskoordinatorsDeltakerlisteProducer = mockk<TiltakskoordinatorsDeltakerlisteProducer>()
    private val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient,
        navAnsattService,
        tiltakskoordinatorTilgangRepository,
        tiltakskoordinatorsDeltakerlisteProducer,
        mockk<TiltakskoordinatorService>(),
        mockk<DeltakerlisteService>(),
    )
    private val deltakerlisteService = mockk<DeltakerlisteService>()

    @BeforeEach
    fun setup() = configureEnvForAuthentication()

    @Test
    fun `get - har ikke tilgang - returnerer 403`() = testApplication {
        val deltaker = TestData.lagDeltaker()
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )
        every { deltakerRepository.get(any()) } returns Result.success(
            TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            ),
        )
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true

        setUpTestApplication()

        client
            .post("/pamelding/${UUID.randomUUID()}/utenGodkjenning") {
                createPostRequest(
                    pameldingUtenGodkjenningRequest(
                        deltaker.deltakelsesinnhold!!.innhold.toInnholdDto(),
                    ),
                )
            }.status shouldBe HttpStatusCode.Forbidden
        client.post("/pamelding/${UUID.randomUUID()}/avbryt") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/pamelding/${UUID.randomUUID()}/utenGodkjenning") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/pamelding/${UUID.randomUUID()}/avbryt") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `post utkast - har tilgang - oppretter utkast og returnerer deltaker`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.KLADD))
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        coEvery { pameldingService.upsertUtkast(any()) } returns deltaker
        every { forslagRepository.getForDeltaker(deltaker.id) } returns emptyList()
        val (ansatte, enhet) = mockAnsatteOgEnhetForDeltaker(deltaker)

        setUpTestApplication()
        client
            .post("/pamelding/${deltaker.id}") { createPostRequest(utkastRequest(deltaker.deltakelsesinnhold!!.innhold.toInnholdDto())) }
            .apply {
                status shouldBe HttpStatusCode.OK

                val expected = DeltakerResponse.fromDeltaker(
                    deltaker = deltaker,
                    ansatte = ansatte,
                    vedtakSistEndretAvEnhet = enhet,
                    digitalBruker = true,
                    forslag = emptyList(),
                )

                bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
            }
    }

    @Test
    fun `post utkast - deltaker finnes ikke - returnerer 404`() = testApplication {
        every { deltakerRepository.get(any()) } throws NoSuchElementException()

        setUpTestApplication()
        client.post("/pamelding/${UUID.randomUUID()}") { createPostRequest(utkastRequest()) }.apply {
            status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `post utkast uten godkjenning - har tilgang - oppretter og returnerer ferdig godkjent deltaker`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING))

        every { deltakerRepository.get(any()) } returns Result.success(deltaker)
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        coEvery { pameldingService.upsertUtkast(any()) } returns deltaker
        every { forslagRepository.getForDeltaker(deltaker.id) } returns emptyList()

        val (ansatte, enhet) = mockAnsatteOgEnhetForDeltaker(deltaker)

        setUpTestApplication()
        client
            .post("/pamelding/${deltaker.id}") { createPostRequest(utkastRequest(deltaker.deltakelsesinnhold!!.innhold.toInnholdDto())) }
            .apply {
                status shouldBe HttpStatusCode.OK

                val expected = DeltakerResponse.fromDeltaker(
                    deltaker = deltaker,
                    ansatte = ansatte,
                    vedtakSistEndretAvEnhet = enhet,
                    digitalBruker = true,
                    forslag = emptyList(),
                )

                bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
            }
    }

    @Test
    fun `post utkast uten godkjenning - deltaker finnes ikke - returnerer 404`() = testApplication {
        every { deltakerRepository.get(any()) } throws NoSuchElementException()

        setUpTestApplication()
        client
            .post("/pamelding/${UUID.randomUUID()}/utenGodkjenning") { createPostRequest(pameldingUtenGodkjenningRequest()) }
            .apply {
                status shouldBe HttpStatusCode.NotFound
            }
    }

    @Test
    fun `avbryt utkast - har tilgang  - avbryter utkast og returnerer 200`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        coEvery { pameldingService.avbrytUtkast(deltaker, any(), any()) } returns Unit

        setUpTestApplication()
        client.post("/pamelding/${deltaker.id}/avbryt") { noBodyRequest() }.apply {
            status shouldBe HttpStatusCode.OK
        }
    }

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                tilgangskontrollService = tilgangskontrollService,
                deltakerRepository = deltakerRepository,
                deltakerService = deltakerService,
                pameldingService = pameldingService,
                navAnsattService = navAnsattService,
                navEnhetService = navEnhetService,
                innbyggerService = mockk(),
                forslagRepository = forslagRepository,
                forslagService = forslagService,
                amtDistribusjonClient = amtDistribusjonClient,
                amtDeltakerClient = amtDeltakerClient,
                sporbarhetsloggService = mockk(),
                deltakerlisteService = deltakerlisteService,
                unleash = mockk(),
                sporbarhetOgTilgangskontrollSvc = mockk(),
                tiltakskoordinatorService = mockk(),
                tiltakskoordinatorTilgangRepository = mockk(),
                ulestHendelseService = mockk(),
                testdataService = mockk(),
            )
        }
    }

    private fun utkastRequest(innhold: List<InnholdRequest> = emptyList()) = UtkastRequest(innhold, "Bakgrunnen for...", null, null)

    private fun pameldingUtenGodkjenningRequest(innhold: List<InnholdRequest> = emptyList()) = PameldingUtenGodkjenningRequest(
        innhold,
        "Bakgrunnen for...",
        null,
        null,
    )

    private fun mockAnsatteOgEnhetForDeltaker(deltaker: Deltaker): Pair<Map<UUID, NavAnsatt>, NavEnhet?> {
        val ansatte = TestData.lagNavAnsatteForDeltaker(deltaker).associateBy { it.id }
        val enhet = deltaker.vedtaksinformasjon?.let { TestData.lagNavEnhet(id = it.sistEndretAvEnhet) }

        every { navAnsattService.hentAnsatteForDeltaker(deltaker) } returns ansatte
        enhet?.let { every { navEnhetService.hentEnhet(it.id) } returns it }

        return Pair(ansatte, enhet)
    }
}

private fun List<Innhold>.toInnholdDto() = this.map {
    InnholdRequest(
        it.innholdskode,
        it.beskrivelse,
    )
}
