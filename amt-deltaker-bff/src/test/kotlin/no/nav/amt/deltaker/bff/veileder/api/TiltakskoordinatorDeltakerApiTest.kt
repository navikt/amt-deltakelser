package no.nav.amt.deltaker.bff.veileder.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.getunleash.Unleash
import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.apiclients.deltaker.AmtDeltakerClient
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
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.toDeltakerStatusAarsak
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.sporbarhet.SporbarhetsloggService
import no.nav.amt.deltaker.bff.tiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.deltaker.bff.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.generateJWT
import no.nav.amt.deltaker.bff.veileder.api.request.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.request.AvvisForslagRequest
import no.nav.amt.deltaker.bff.veileder.api.request.DeltakerRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreAvslutningRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreBakgrunnsinformasjonRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreDeltakelsesmengdeRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreInnholdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreSluttarsakRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreSluttdatoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreStartdatoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.FjernOppstartsdatoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.ForlengDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.request.IkkeAktuellRequest
import no.nav.amt.deltaker.bff.veileder.api.request.InnholdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.request.toInnholdModel
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.response.toResponse
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltakskoordinatorDeltakerApiTest {
    private val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val deltakerService = mockk<DeltakerService>()
    private val pameldingService = mockk<PameldingService>()
    private val navAnsattService = mockk<NavAnsattService>()
    private val navEnhetService = mockk<NavEnhetService>()
    private val forslagRepository = mockk<ForslagRepository>()
    private val forslagService = mockk<ForslagService>(relaxed = true)
    private val amtDistribusjonClient = mockk<AmtDistribusjonClient>()
    private val amtDeltakerClient = mockk<AmtDeltakerClient>()
    private val sporbarhetsloggService = mockk<SporbarhetsloggService>(relaxed = true)
    private val unleash = mockk<Unleash>()
    private val unleashToggle = CommonUnleashToggle(unleash)
    private val tiltakskoordinatorTilgangRepository = mockk<TiltakskoordinatorTilgangRepository>()
    private val tiltakskoordinatorsDeltakerlisteProducer = mockk<TiltakskoordinatorsDeltakerlisteProducer>()
    private val deltakerlisteService = mockk<DeltakerlisteService>()
    private val tiltakskoordinatorService = mockk<TiltakskoordinatorService>()
    private val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient,
        navAnsattService,
        tiltakskoordinatorTilgangRepository,
        tiltakskoordinatorsDeltakerlisteProducer,
        tiltakskoordinatorService,
        deltakerlisteService,
    )

    @BeforeEach
    fun setup() {
        configureEnvForAuthentication()
        clearMocks(sporbarhetsloggService)
    }

    @Test
    fun `skal teste tilgangskontroll - har ikke tilgang - returnerer 403`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )
        every {
            deltakerRepository.get(any())
        } returns Result.success(TestData.lagDeltaker(navBruker = TestData.lagNavBruker(personident = "1234")))
        every { forslagRepository.get(any()) } returns Result.success(TestData.lagForslag())
        every { unleash.isEnabled("amt.prioriter-synkron-kommunikasjon") } returns false

        setUpTestApplication()
        client
            .post(
                "/deltaker/${UUID.randomUUID()}/bakgrunnsinformasjon",
            ) { createPostRequest(bakgrunnsinformasjonRequest) }
            .status shouldBe
            HttpStatusCode.Forbidden
        client.post("/deltaker/${UUID.randomUUID()}/innhold") { createPostRequest(innholdRequest) }.status shouldBe HttpStatusCode.Forbidden
        client.post("/deltaker/${UUID.randomUUID()}/deltakelsesmengde") { createPostRequest(deltakelsesmengdeRequest) }.status shouldBe
            HttpStatusCode.Forbidden
        client.post("/deltaker/${UUID.randomUUID()}/startdato") { createPostRequest(startdatoRequest) }.status shouldBe
            HttpStatusCode.Forbidden
        client.post("/deltaker/${UUID.randomUUID()}/sluttdato") { createPostRequest(sluttdatoRequest) }.status shouldBe
            HttpStatusCode.Forbidden
        client
            .post(
                "/deltaker/${UUID.randomUUID()}/ikke-aktuell",
            ) { createPostRequest(ikkeAktuellRequest) }
            .status shouldBe HttpStatusCode.Forbidden
        client
            .post(
                "/deltaker/${UUID.randomUUID()}/forleng",
            ) { createPostRequest(forlengDeltakelseRequest) }
            .status shouldBe HttpStatusCode.Forbidden
        client
            .post(
                "/deltaker/${UUID.randomUUID()}/avslutt",
            ) { createPostRequest(avsluttDeltakelseRequest) }
            .status shouldBe HttpStatusCode.Forbidden
        client
            .post(
                "/deltaker/${UUID.randomUUID()}/endre-avslutning",
            ) { createPostRequest(endreAvslutningRequest) }
            .status shouldBe HttpStatusCode.Forbidden
        client
            .post("/deltaker/${UUID.randomUUID()}") {
                createPostRequest(deltakerRequest)
            }.status shouldBe HttpStatusCode.Forbidden
        client.get("/deltaker/${UUID.randomUUID()}/historikk") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
        client
            .post(
                "/deltaker/${UUID.randomUUID()}/reaktiver",
            ) { createPostRequest(reaktiverDeltakelseRequest) }
            .status shouldBe HttpStatusCode.Forbidden
        client.post("/forslag/${UUID.randomUUID()}/avvis") { createPostRequest(avvisForslagRequest) }.status shouldBe
            HttpStatusCode.Forbidden
        client
            .post("/deltaker/${UUID.randomUUID()}/fjern-oppstartsdato") {
                createPostRequest(fjernOppstartsdatoRequest)
            }.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/deltaker/${UUID.randomUUID()}/bakgrunnsinformasjon") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/innhold") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/deltakelsesmengde") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/startdato") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/sluttdato") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/ikke-aktuell") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/forleng") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/avslutt") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/reaktiver") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/fjern-oppstartsdato") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.get("/deltaker/${UUID.randomUUID()}/historikk").status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/endre-avslutning").status shouldBe HttpStatusCode.Unauthorized
        client.post("/forslag/${UUID.randomUUID()}/avvis").status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `oppdater bakgrunnsinformasjon - har tilgang - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker =
            TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            bakgrunnsinformasjon = bakgrunnsinformasjonRequest.bakgrunnsinformasjon,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client
            .post("/deltaker/${deltaker.id}/bakgrunnsinformasjon") { createPostRequest(bakgrunnsinformasjonRequest) }
            .apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
    }

    @Test
    fun `oppdater bakgrunnsinformasjon - deltaker har sluttet - returnerer bad request`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusMonths(3),
            ),
            sluttdato = LocalDate.now().minusMonths(3),
        )

        setupMocks(deltaker, null)

        client
            .post("/deltaker/${deltaker.id}/bakgrunnsinformasjon") { createPostRequest(bakgrunnsinformasjonRequest) }
            .apply {
                status shouldBe HttpStatusCode.BadRequest
            }
    }

    @Test
    fun `oppdater innhold - har tilgang - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker =
            TestData.lagDeltaker(status = TestData.lagDeltakerStatus(statusType = DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            deltakelsesinnhold = Deltakelsesinnhold("ledetekst", innholdRequest.innhold.toInnholdModel(deltaker)),
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client
            .post("/deltaker/${deltaker.id}/innhold") {
                createPostRequest(EndreInnholdRequest(listOf(InnholdRequest(deltaker.deltakelsesinnhold!!.innhold[0].innholdskode, null))))
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
    }

    @Test
    fun `oppdater deltakelsesmengde - har tilgang - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker =
            TestData.lagDeltaker(
                sluttdato = LocalDate.now().plusMonths(3),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

        val oppdatertDeltaker = deltaker.copy(
            dagerPerUke = deltakelsesmengdeRequest.dagerPerUke?.toFloat(),
            deltakelsesprosent = deltakelsesmengdeRequest.deltakelsesprosent?.toFloat(),
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/deltakelsesmengde") { createPostRequest(deltakelsesmengdeRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `oppdater deltakelsesmengde - ingen endring - returnerer BadRequest`() = testApplication {
        setUpTestApplication()
        val deltaker =
            TestData.lagDeltaker(
                sluttdato = LocalDate.now().plusMonths(3),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
        setupMocks(deltaker, null)

        client
            .post("/deltaker/${deltaker.id}/deltakelsesmengde") {
                createPostRequest(
                    EndreDeltakelsesmengdeRequest(
                        deltakelsesprosent = deltaker.deltakelsesprosent?.toInt(),
                        dagerPerUke = deltaker.dagerPerUke?.toInt(),
                        begrunnelse = "begrunnelse",
                        gyldigFra = LocalDate.now(),
                        forslagId = null,
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
    }

    @Test
    fun `oppdater startdato - har tilgang - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker =
            TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = startdatoRequest.startdato,
            sluttdato = sluttdatoRequest.sluttdato,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/startdato") { createPostRequest(startdatoRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `endre sluttdato - har tilgang, deltaker har status HAR SLUTTET - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusDays(3),
        )
        val oppdatertDeltaker = deltaker.copy(
            sluttdato = sluttdatoRequest.sluttdato,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/sluttdato") { createPostRequest(sluttdatoRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `endre sluttdato - har tilgang, deltaker har status IKKE AKTUELL - feiler`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            sluttdato = LocalDate.now().minusDays(3),
        )
        setupMocks(deltaker, null)

        client.post("/deltaker/${deltaker.id}/sluttdato") { createPostRequest(sluttdatoRequest) }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `ikke aktuell - har tilgang - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(
                DeltakerStatus.Type.IKKE_AKTUELL,
                ikkeAktuellRequest.aarsak.toDeltakerStatusAarsak(),
            ),
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/ikke-aktuell") { createPostRequest(ikkeAktuellRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `endre sluttarsak - har tilgang, deltaker har status HAR SLUTTET - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        )

        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.HAR_SLUTTET,
                aarsak = sluttarsakRequest.aarsak.toDeltakerStatusAarsak(),
            ),
        )
        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/sluttarsak") { createPostRequest(sluttarsakRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `getDeltaker - har tilgang, deltaker finnes - returnerer deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            navBruker = TestData.lagNavBruker(personident = "1234"),
        )

        val expectedDeltakerResponse = deltakerResponseInTest(deltaker, setupMocks(deltaker, deltaker))

        client.post("/deltaker/${deltaker.id}") { createPostRequest(deltakerRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
        verify(exactly = 1) { sporbarhetsloggService.sendAuditLog(any(), any()) }
    }

    @Test
    fun `getDeltaker - har annen navBruker i kontekst, deltaker finnes - returnerer badRequest`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            navBruker = TestData.lagNavBruker(personident = "4321"),
        )
        setupMocks(deltaker, null)

        client.post("/deltaker/${deltaker.id}") { createPostRequest(deltakerRequest) }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `getDeltaker - deltaker er importert fra arena - returnerer importertFraArenaDto`() = testApplication {
        setUpTestApplication()
        val innsoktDatoFraArena = LocalDate.now().minusDays(5)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            navBruker = TestData.lagNavBruker(personident = "1234"),
            innsoktDatoFraArena = innsoktDatoFraArena,
        )
        setupMocks(deltaker, null)

        client.post("/deltaker/${deltaker.id}") { createPostRequest(deltakerRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            val responseText = bodyAsText()
            val deltakerResponse = objectMapper.readValue<DeltakerResponse>(responseText)
            deltakerResponse.importertFraArena?.innsoktDato shouldBe innsoktDatoFraArena
            deltakerResponse.vedtaksinformasjon shouldBe null
        }
        verify(exactly = 1) { sporbarhetsloggService.sendAuditLog(any(), any()) }
    }

    @Test
    fun `getDeltakerHistorikk - har tilgang, deltaker finnes - returnerer historikk`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker().let { TestData.leggTilHistorikk(it, 2, 2, 1) }
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

        val historikk = deltaker.getDeltakerHistorikkForVisning()
        val ansatte = TestData.lagNavAnsatteForHistorikk(historikk).associateBy { it.id }
        val enheter = TestData.lagNavEnheterForHistorikk(historikk).associateBy { it.id }

        every { unleashToggle.prioriterSynkronKommunikasjon() } returns true
        every { navAnsattService.hentAnsatteForHistorikk(historikk) } returns ansatte
        coEvery { deltakerService.hentDeltakerHistorikk(any()) } returns historikk
        coEvery { navEnhetService.hentEnheterForHistorikk(historikk) } returns enheter
        client.get("/deltaker/${deltaker.id}/historikk") { noBodyRequest() }.apply {
            status shouldBe HttpStatusCode.OK
            val res = bodyAsText()
            val json = objectMapper.writePolymorphicListAsString(
                historikk.toResponse(
                    ansatte,
                    deltaker.deltakerliste.arrangor.getArrangorNavn(),
                    enheter,
                    deltaker.deltakerliste.oppstart,
                ),
            )
            res shouldBe json
        }
    }

    @Test
    fun `forleng - har tilgang - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = forlengDeltakelseRequest.sluttdato.minusDays(3),
        )
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = forlengDeltakelseRequest.sluttdato,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/forleng") { createPostRequest(forlengDeltakelseRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `forleng - har tilgang, ny dato tidligere enn forrige dato - feiler`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(sluttdato = forlengDeltakelseRequest.sluttdato.plusDays(5))
        setupMocks(deltaker, null)

        client.post("/deltaker/${deltaker.id}/forleng") { createPostRequest(forlengDeltakelseRequest) }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `forleng - har tilgang, har sluttet for mer enn to mnd siden - feiler`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusMonths(3),
            ),
            sluttdato = forlengDeltakelseRequest.sluttdato.minusMonths(3),
        )
        setupMocks(deltaker, null)

        client.post("/deltaker/${deltaker.id}/forleng") { createPostRequest(forlengDeltakelseRequest) }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `forleng - har tilgang, ikke under oppfolging - feiler`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(
                oppfolgingsperioder = listOf(
                    TestData.lagOppfolgingsperiode(
                        startdato = LocalDateTime.now().minusMonths(2),
                        sluttdato = LocalDateTime.now().minusDays(2),
                    ),
                ),
            ),
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = forlengDeltakelseRequest.sluttdato.minusDays(3),
        )
        setupMocks(deltaker, null)

        client.post("/deltaker/${deltaker.id}/forleng") { createPostRequest(forlengDeltakelseRequest) }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `avslutt - har tilgang, har deltatt - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(
                DeltakerStatus.Type.HAR_SLUTTET,
                avsluttDeltakelseRequest.aarsak!!.toDeltakerStatusAarsak(),
            ),
            sluttdato = avsluttDeltakelseRequest.sluttdato,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `avslutt - har tilgang, har deltatt, mangler sluttdato - feiler`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(
                DeltakerStatus.Type.HAR_SLUTTET,
                avsluttDeltakelseRequest.aarsak!!.toDeltakerStatusAarsak(),
            ),
            sluttdato = avsluttDeltakelseRequest.sluttdato,
        )
        val avsluttDeltakelseRequestUtenSluttdato = AvsluttDeltakelseRequest(
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB),
            sluttdato = null,
            harDeltatt = true,
            begrunnelse = null,
            forslagId = null,
        )
        setupMocks(deltaker, oppdatertDeltaker)

        client.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequestUtenSluttdato) }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `avslutt - har tilgang, har ikke deltatt - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(
                DeltakerStatus.Type.IKKE_AKTUELL,
                avsluttDeltakelseRequest.aarsak!!.toDeltakerStatusAarsak(),
            ),
            startdato = null,
            sluttdato = null,
        )
        val avsluttDeltakelseRequestIkkeDeltatt = AvsluttDeltakelseRequest(
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT),
            sluttdato = null,
            harDeltatt = false,
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequestIkkeDeltatt) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `avslutt - har tilgang, har ikke deltatt, mer enn 15 dager siden - feiler ikke`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.DELTAR,
                gyldigFra = LocalDateTime.now().minusDays(20),
            ),
        )
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(
                DeltakerStatus.Type.IKKE_AKTUELL,
                avsluttDeltakelseRequest.aarsak!!.toDeltakerStatusAarsak(),
            ),
            startdato = null,
            sluttdato = null,
        )
        val avsluttDeltakelseRequestIkkeDeltatt = AvsluttDeltakelseRequest(
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT),
            sluttdato = null,
            harDeltatt = false,
            begrunnelse = null,
            forslagId = null,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequestIkkeDeltatt) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `avslutt - har tilgang, status VENTER PA OPPSTART - feiler`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        setupMocks(deltaker, null)

        client.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequest) }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `endre-avslutning til avbrutt- har tilgang, har fullfort- returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.FULLFORT))
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(
                DeltakerStatus.Type.AVBRUTT,
                DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB).toDeltakerStatusAarsak(),
            ),
        )
        val endreAvslutningRequestAvbrutt = EndreAvslutningRequest(
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB),
            harDeltatt = null,
            harFullfort = false,
            begrunnelse = "begrunnelse",
            sluttdato = deltaker.sluttdato,
            forslagId = null,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/endre-avslutning") { createPostRequest(endreAvslutningRequestAvbrutt) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `endre-avslutning til fullfort- har tilgang, har avbrutt- returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                DeltakerStatus.Type.AVBRUTT,
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB).toDeltakerStatusAarsak(),
            ),
        )
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(
                DeltakerStatus.Type.FULLFORT,
                null,
            ),
        )
        val endreAvslutningRequestAvbrutt = EndreAvslutningRequest(
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB),
            harDeltatt = null,
            harFullfort = true,
            begrunnelse = "begrunnelse",
            sluttdato = deltaker.sluttdato,
            forslagId = null,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/endre-avslutning") { createPostRequest(endreAvslutningRequestAvbrutt) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `reaktiver - har tilgang - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker =
            TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL))
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client
            .post("/deltaker/${deltaker.id}/reaktiver") { createPostRequest(reaktiverDeltakelseRequest) }
            .apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
    }

    @Test
    fun `reaktiver - deltaker har sluttet - returnerer bad request`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusMonths(3),
            ),
            sluttdato = LocalDate.now().minusMonths(1),
        )
        setupMocks(deltaker, null)

        client
            .post("/deltaker/${deltaker.id}/reaktiver") { createPostRequest(reaktiverDeltakelseRequest) }
            .apply {
                status shouldBe HttpStatusCode.BadRequest
            }
    }

    @Test
    fun `fjern oppstartsdato - har tilgang - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().plusWeeks(1),
            sluttdato = LocalDate.now().plusMonths(3),
        )
        val oppdatertDeltaker = deltaker.copy(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        client.post("/deltaker/${deltaker.id}/fjern-oppstartsdato") { createPostRequest(fjernOppstartsdatoRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
        }
    }

    @Test
    fun `avvis forslag - har tilgang - returnerer oppdatert deltaker`() = testApplication {
        setUpTestApplication()
        val deltaker = TestData.lagDeltaker()
        val forslag = TestData.lagForslag(deltakerId = deltaker.id)
        every { forslagRepository.get(forslag.id) } returns Result.success(forslag)

        val expectedDeltakerResponse = deltakerResponseInTest(deltaker, setupMocks(deltaker, deltaker))

        client
            .post("/forslag/${forslag.id}/avvis") { createPostRequest(avvisForslagRequest) }
            .apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
    }

    private fun HttpRequestBuilder.noBodyRequest() {
        bearerAuth(
            generateJWT(
                consumerClientId = "frontend-clientid",
                navAnsattAzureId = UUID.randomUUID().toString(),
                audience = "deltaker-bff",
            ),
        )
        header("aktiv-enhet", "0101")
    }

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
                innbyggerService = mockk(),
                forslagRepository = forslagRepository,
                forslagService = forslagService,
                amtDistribusjonClient = amtDistribusjonClient,
                amtDeltakerClient = amtDeltakerClient,
                sporbarhetsloggService = sporbarhetsloggService,
                deltakerRepository = deltakerRepository,
                deltakerlisteService = mockk(),
                unleash = unleash,
                sporbarhetOgTilgangskontrollSvc = mockk(),
                tiltakskoordinatorService = mockk(),
                tiltakskoordinatorTilgangRepository = mockk(),
                ulestHendelseService = mockk(),
                testdataService = mockk(),
            )
        }
    }

    private val deltakerRequest = DeltakerRequest("1234")
    private val bakgrunnsinformasjonRequest = EndreBakgrunnsinformasjonRequest("Oppdatert bakgrunnsinformasjon")
    private val innholdRequest = EndreInnholdRequest(listOf(InnholdRequest("annet", "beskrivelse")))
    private val deltakelsesmengdeRequest = EndreDeltakelsesmengdeRequest(
        deltakelsesprosent = 50,
        dagerPerUke = 3,
        begrunnelse = "begrunnelse",
        gyldigFra = LocalDate.now(),
        forslagId = null,
    )
    private val startdatoRequest =
        EndreStartdatoRequest(LocalDate.now().plusWeeks(1), sluttdato = LocalDate.now().plusMonths(2), "begrunnelse", null)
    private val ikkeAktuellRequest = IkkeAktuellRequest(DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB), "begrunnelse", null)
    private val reaktiverDeltakelseRequest = ReaktiverDeltakelseRequest("begrunnelse")
    private val forlengDeltakelseRequest = ForlengDeltakelseRequest(LocalDate.now().plusWeeks(3), "begrunnelse", null)
    private val avsluttDeltakelseRequest =
        AvsluttDeltakelseRequest(
            DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB),
            LocalDate.now(),
            harDeltatt = true,
            harFullfort = null,
            "begrunnelse",
            null,
        )
    private val endreAvslutningRequest =
        EndreAvslutningRequest(
            DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB),
            harDeltatt = true,
            harFullfort = null,
            "begrunnelse",
            sluttdato = null,
            null,
        )
    private val sluttdatoRequest = EndreSluttdatoRequest(LocalDate.now().minusDays(1), "begrunnelse", null)
    private val sluttarsakRequest =
        EndreSluttarsakRequest(DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT), "begrunnelse", null)
    private val avvisForslagRequest = AvvisForslagRequest("Avvist fordi..")
    private val fjernOppstartsdatoRequest = FjernOppstartsdatoRequest("begrunnelse", null)

    private fun setupMocks(
        deltaker: Deltaker,
        oppdatertDeltaker: Deltaker?,
        forslag: List<Forslag> = emptyList(),
    ): Pair<Map<UUID, NavAnsatt>, NavEnhet?> {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        every { deltakerRepository.getMany(deltaker.navBruker.personident, deltaker.deltakerliste.id) } returns listOf(deltaker)
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        every { forslagRepository.getForDeltaker(deltaker.id) } returns forslag
        every { unleash.isEnabled("amt.prioriter-synkron-kommunikasjon") } returns false

        return if (oppdatertDeltaker != null) {
            coEvery {
                deltakerService.oppdaterDeltaker(deltaker = deltaker, endringRequest = any())
            } returns oppdatertDeltaker

            mockAnsatteOgEnhetForDeltaker(oppdatertDeltaker)
        } else {
            mockAnsatteOgEnhetForDeltaker(deltaker)
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

    companion object {
        private fun deltakerResponseInTest(
            deltaker: Deltaker,
            mocks: Pair<Map<UUID, NavAnsatt>, NavEnhet?>,
        ) = DeltakerResponse.fromDeltaker(
            deltaker = deltaker,
            ansatte = mocks.first,
            vedtakSistEndretAvEnhet = mocks.second,
            digitalBruker = true,
            forslag = emptyList(),
        )
    }
}
