package no.nav.amt.deltaker.bff.veileder.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
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
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.tiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.bff.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.veileder.api.request.InnholdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.KladdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.OpprettNyKladdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.UtkastRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.noBodyRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.utils.objectMapper
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class KladdApiTest {
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
    fun `post kladd - har tilgang - returnerer deltaker`() = testApplication {
        val deltaker = TestData.lagDeltaker()
        val ansatte = TestData.lagNavAnsatteForDeltaker(deltaker).associateBy { it.id }
        val navEnhet = TestData.lagNavEnhet(id = deltaker.vedtaksinformasjon!!.sistEndretAvEnhet)

        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        coEvery { pameldingService.opprettKladd(any(), any()) } returns deltaker
        every { navAnsattService.hentAnsatteForDeltaker(deltaker) } returns ansatte
        every { navEnhetService.hentEnhet(navEnhet.id) } returns navEnhet
        every { forslagRepository.getForDeltaker(deltaker.id) } returns emptyList()
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true

        setUpTestApplication()

        client.post("/kladd") { createPostRequest(opprettNyKladdRequest) }.apply {
            assertEquals(HttpStatusCode.OK, status)

            val expected = DeltakerResponse.fromDeltaker(
                deltaker = deltaker,
                ansatte = ansatte,
                vedtakSistEndretAvEnhet = navEnhet,
                digitalBruker = true,
                forslag = emptyList(),
            )

            bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
        }
    }

    @Test
    fun `post - har ikke tilgang - returnerer 403`() = testApplication {
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

        client.post("/kladd") { createPostRequest(opprettNyKladdRequest) }.status shouldBe HttpStatusCode.Forbidden
        client
            .post("/kladd/${UUID.randomUUID()}") {
                createPostRequest(utkastRequest(deltaker.deltakelsesinnhold!!.innhold.toInnholdDto()))
            }.status shouldBe HttpStatusCode.Forbidden
        client.post("/kladd/${UUID.randomUUID()}") { createPostRequest(kladdRequest) }.status shouldBe HttpStatusCode.Forbidden
        client.delete("/kladd/${UUID.randomUUID()}") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `post kladd - har tilgang - returnerer 200`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.KLADD))
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

        coEvery { pameldingService.upsertKladd(any()) } returns deltaker

        setUpTestApplication()
        client.post("/kladd/${deltaker.id}") { createPostRequest(kladdRequest) }.apply {
            status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `post kladd - feil deltakerstatus - returnerer 400`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

        coEvery { pameldingService.upsertKladd(any()) } throws IllegalArgumentException()

        setUpTestApplication()
        client.post("/kladd/${deltaker.id}") { createPostRequest(kladdRequest) }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `slett kladd - deltaker har ikke status KLADD - returnerer 400`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker =
            TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING))
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        coEvery { pameldingService.slettKladd(deltaker) } returns false

        setUpTestApplication()
        client.delete("/kladd/${deltaker.id}") { noBodyRequest() }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `slett kladd - deltaker er KLADD - sletter deltaker og returnerer 200`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.KLADD))
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        coEvery { pameldingService.slettKladd(deltaker) } returns true

        setUpTestApplication()
        client.delete("/kladd/${deltaker.id}") { noBodyRequest() }.apply {
            status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `post - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/kladd") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/kladd/${UUID.randomUUID()}") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/kladd/${UUID.randomUUID()}") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.delete("/kladd/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `post kladd - deltakerliste finnes ikke - returnerer 404`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        coEvery {
            pameldingService.opprettKladd(any(), any())
        } throws NoSuchElementException("Deltaker ikke funnet")

        setUpTestApplication()

        val response = client.post("/kladd") { createPostRequest(opprettNyKladdRequest) }

        response.status shouldBe HttpStatusCode.NotFound
    }

    fun ApplicationTestBuilder.setUpTestApplication() {
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

    private val kladdRequest = KladdRequest(emptyList(), "Bakgrunnen for...", null, null)
    private val opprettNyKladdRequest = OpprettNyKladdRequest(UUID.randomUUID(), "1234")
}

private fun List<Innhold>.toInnholdDto() = this.map {
    InnholdRequest(
        it.innholdskode,
        it.beskrivelse,
    )
}
