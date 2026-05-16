package no.nav.amt.deltaker.bff.navtiltakskoordinator

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteStengtException
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerResponseUtils
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerlisteResponse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseBuilder
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseMapper
import no.nav.amt.deltaker.bff.navtiltakskoordinator.model.Tiltakskoordinator
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.TestData.lagGjennomforingResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakskoordinatorDeltakerResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakskoordinatorNavBrukerResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakskoordinatorTilgang
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostTiltakskoordinatorRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.noBodyRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.noBodyTiltakskoordinatorRequest
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorDeltakereResponse
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import org.junit.jupiter.api.Test
import java.util.UUID

class TiltakskoordinatorDeltakerlisteApiTest : IntegrationTestBase() {
    override val tiltakskoordinatorResponseBuilder = ResponseBuilder(ulestHendelseRepository)

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.get("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
            client.get("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere").status shouldBe HttpStatusCode.Unauthorized
            client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/tilgang/legg-til").status shouldBe
                HttpStatusCode.Unauthorized
            client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere/del-med-arrangor").status shouldBe
                HttpStatusCode.Unauthorized
            client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere/sett-paa-venteliste").status shouldBe
                HttpStatusCode.Unauthorized
            client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere/gi-avslag").status shouldBe
                HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal teste autentisering - mangler AD rolle - returnerer 401`() {
        every { deltakerlisteService.get(deltakerlisteInTest.id) } returns Result.success(deltakerlisteInTest)

        withTestApplicationContext { client ->
            client
                .get("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}") { noBodyRequest() }
                .apply { status shouldBe HttpStatusCode.Unauthorized }

            client
                .get("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") { noBodyRequest() }
                .apply { status shouldBe HttpStatusCode.Unauthorized }

            client
                .post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/tilgang/legg-til") { noBodyRequest() }
                .apply { status shouldBe HttpStatusCode.Unauthorized }

            client
                .post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/del-med-arrangor") { noBodyRequest() }
                .apply { status shouldBe HttpStatusCode.Unauthorized }

            client
                .post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/sett-paa-venteliste") { noBodyRequest() }
                .apply { status shouldBe HttpStatusCode.Unauthorized }

            client
                .post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/gi-avslag") { noBodyRequest() }
                .apply { status shouldBe HttpStatusCode.Unauthorized }
        }
    }

    @Test
    fun `get deltakerliste - liste finnes ikke, toggle på - returnerer 404`() {
        coEvery {
            gjennomforingClient.getGjennomforing(any())
        } throws NoSuchElementException()
        every { navAnsattService.hentNavAnsatt(any()) } returns lagNavAnsatt()
        every { tiltakskoordinatorTilgangRepository.hentKoordinatorer(any(), any()) } returns emptyList()
        every { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns true

        val response = withTestApplicationContext { client ->
            client.get("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}") {
                noBodyTiltakskoordinatorRequest()
            }
        }

        response.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `get deltakerliste - liste finnes - returnerer 200 og liste`() {
        // Arrange
        val gjennomforing = lagGjennomforingResponse()
        val expected = ResponseMapper.buildGjennomforing(gjennomforing, listOf(tiltakskoordinatorInTest))
        every { navAnsattService.hentNavAnsatt(any()) } returns lagNavAnsatt()
        every { deltakerlisteService.get(deltakerlisteInTest.id) } returns Result.success(deltakerlisteInTest)
        coEvery { gjennomforingClient.getGjennomforing(deltakerlisteInTest.id) } returns gjennomforing

        every {
            tiltakskoordinatorTilgangRepository.hentKoordinatorer(
                deltakerlisteId = any(),
                paaloggetNavAnsattId = any(),
            )
        } returns listOf(tiltakskoordinatorInTest)
        every { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns true

        withTestApplicationContext { client ->
            // Act
            val response = client.get("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}") {
                noBodyTiltakskoordinatorRequest()
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK
            val deltakerlisteResponse = response.body<DeltakerlisteResponse>()
            deltakerlisteResponse shouldBe expected
        }
    }

    @Test
    fun `get deltakere - mangler tilgang til deltakerliste - returnerer 403`() {
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
        coEvery { selfServiceTilgangskontrollService.verifiserTiltakskoordinatorTilgang(any(), any()) } throws AuthorizationException("")

        val response = withTestApplicationContext { client ->
            client.get("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") {
                noBodyTiltakskoordinatorRequest()
            }
        }

        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `get deltakere - deltakerliste finnes ikke - returnerer 404`() {
        mockTilgangTilDeltakerliste()

        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(any()) } throws NoSuchElementException()

        // every { (any()) } returns emptyList()
        val response = withTestApplicationContext { client ->
            client.get("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere") {
                noBodyTiltakskoordinatorRequest()
            }
        }

        response.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `get deltakere - deltakerliste er stengt - returnerer 410`() {
        mockTilgangTilDeltakerliste()

        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(any()) } throws DeltakerlisteStengtException()

        val response = withTestApplicationContext { client ->
            client.get("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere") {
                noBodyTiltakskoordinatorRequest()
            }
        }

        response.status shouldBe HttpStatusCode.Gone
    }

    @Test
    fun `get deltakere - deltakerliste finnes - returnerer liste med deltakere`() {
        mockTilgangTilDeltakerliste()

        val deltakere = (0..5)
            .map { lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR)) }
        val deltakereResponse = TiltakskoordinatorDeltakereResponse(gjennomforing = null, deltakere)

        coEvery { tiltakskoordinatorClient.getDeltakereForGjennomforing(deltakerlisteInTest.id) } returns deltakereResponse
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
        every { ulestHendelseRepository.getForDeltakere(any()) } returns emptyMap()

        deltakere.forEach {
            every {
                tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                    any(),
                    adressebeskyttelse = it.navBruker.adressebeskyttelse,
                    erSkjermet = it.navBruker.erSkjermet,
                )
            } returns true
        }

        withTestApplicationContext { client ->
            val response = client.get("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") {
                noBodyTiltakskoordinatorRequest()
            }

            response.status shouldBe HttpStatusCode.OK

            val actualResponse = response.body<List<DeltakerResponse>>()
            actualResponse.size shouldBe deltakere.size
            actualResponse.map { it.id } shouldBe deltakere.map { it.id }
        }
    }

    @Test
    fun `get deltakere - returnerer alle deltakere fra amt-deltaker uten ekstra filtering`() {
        mockTilgangTilDeltakerliste()

        val deltaker1 = lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val deltaker2 = lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))

        coEvery { tiltakskoordinatorClient.getDeltakereForGjennomforing(deltakerlisteInTest.id) } returns
            TiltakskoordinatorDeltakereResponse(gjennomforing = null, listOf(deltaker1, deltaker2))
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
        every { ulestHendelseRepository.getForDeltakere(any()) } returns emptyMap()
        every {
            tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(any(), any(), any())
        } returns true

        withTestApplicationContext { client ->
            val response = client.get("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") {
                noBodyTiltakskoordinatorRequest()
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<List<DeltakerResponse>>()
            body.size shouldBe 2
            body.map { it.id }.toSet() shouldBe setOf(deltaker1.id, deltaker2.id)
        }
    }

    @Test
    fun `get deltakere - mangler tilgang til skjermet person - kanSeInnbyggersNavn er false`() {
        mockTilgangTilDeltakerliste()

        val skjermetDeltaker = lagTiltakskoordinatorDeltakerResponse(
            navBruker = lagTiltakskoordinatorNavBrukerResponse(erSkjermet = true),
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        coEvery { tiltakskoordinatorClient.getDeltakereForGjennomforing(deltakerlisteInTest.id) } returns
            TiltakskoordinatorDeltakereResponse(gjennomforing = null, listOf(skjermetDeltaker))
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
        every { ulestHendelseRepository.getForDeltakere(any()) } returns emptyMap()
        every {
            tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                any(),
                erSkjermet = true,
                adressebeskyttelse = skjermetDeltaker.navBruker.adressebeskyttelse,
            )
        } returns false

        withTestApplicationContext { client ->
            val response = client.get("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") {
                noBodyTiltakskoordinatorRequest()
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<List<DeltakerResponse>>()
            body.size shouldBe 1
            // navnefelt skal være maskert når kanSeInnbyggersNavn=false (skjermet person)
            body.single().fornavn shouldBe DeltakerResponseUtils.SKJERMET_PERSON_PLACEHOLDER_NAVN
        }
    }

    @Test
    fun `legg til tilgang - har ikke tilgang fra for - returnerer 200`() {
        coEvery {
            selfServiceTilgangskontrollService.leggTilTiltakskoordinatorTilgang(
                any(),
                deltakerlisteInTest.id,
            )
        } returns Result.success(lagTiltakskoordinatorTilgang())

        val response = withTestApplicationContext { client ->
            client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/tilgang/legg-til") {
                noBodyTiltakskoordinatorRequest()
            }
        }

        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `legg til tilgang - har tilgang fra for - returnerer 400`() {
        coEvery {
            selfServiceTilgangskontrollService.leggTilTiltakskoordinatorTilgang(
                any(),
                any(),
            )
        } returns Result.failure(IllegalArgumentException())

        coEvery {
            tiltakskoordinatorTilgangRepository.hentAktivTilgang(any(), any())
        } returns Result.success(lagTiltakskoordinatorTilgang())

        val response = withTestApplicationContext { client ->
            client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/tilgang/legg-til") {
                noBodyTiltakskoordinatorRequest()
            }
        }

        response.status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun `post del-med-arrangor - mangler tilgang til deltakerliste - returnerer 403`() {
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
        coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilDeltakereGuard(any(), any(), any()) } throws
            AuthorizationException("")

        val response = withTestApplicationContext { client ->
            client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/del-med-arrangor") {
                createPostTiltakskoordinatorRequest(listOf(UUID.randomUUID()))
            }
        }

        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `post del-med-arrangor - deltakerliste finnes ikke - returnerer 404`() {
        mockTilgangTilDeltakerliste()

        coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilDeltakereGuard(any(), any(), any()) } throws NoSuchElementException()

        val response = withTestApplicationContext { client ->
            client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere/del-med-arrangor") {
                createPostTiltakskoordinatorRequest(listOf(UUID.randomUUID()))
            }
        }

        response.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `post sett-paa-venteliste - deltakerliste er feil type - returnerer unauthorized`() {
        mockTilgangTilDeltakerliste()

        coEvery { selfServiceTilgangskontrollService.verifiserTiltakskoordinatorTilgang(any(), any()) } returns Unit
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(any()) } throws NoSuchElementException()

        val response = withTestApplicationContext { client ->
            client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere/del-med-arrangor") {
                createPostRequest(listOf(UUID.randomUUID()))
            }
        }

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    /*
        @Test
        fun `sett-paa-venteliste - deltakere i feil liste - returnerer 401`() {
            val deltaker1 = TestData.lagDeltaker()
            val deltaker2 = TestData.lagDeltaker(deltakerliste = TestData.lagDeltakerliste(id = UUID.randomUUID()))
            coEvery { deltakerService.getDeltakelser(any()) } returns listOf(deltaker1, deltaker2)
            coEvery { unleashToggle.erKometMasterForTiltakstype(deltaker1.deltakerliste.tiltakstype.arenaKode) } returns true

            val request = DeltakereRequest(
                deltakere = listOf(deltaker1.id, deltaker2.id),
                deltakerlisteId = deltaker1.deltakerliste.id,
                endretAv = "Nav Veiledersen"
            )
            setUpTestApplication()
            client.post("$apiPath/sett-paa-venteliste") { postRequest(request) }.apply {
                status shouldBe HttpStatusCode.Forbidden
                bodyAsText() shouldBe ""
            }
        }
     */

    private fun mockTilgangTilDeltakerliste() {
        coEvery { selfServiceTilgangskontrollService.verifiserTiltakskoordinatorTilgang(any(), any()) } returns Unit
    }

    companion object {
        private val deltakerlisteInTest = lagDeltakerliste(pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK)

        private val tiltakskoordinatorInTest = Tiltakskoordinator(
            id = UUID.randomUUID(),
            navn = "~navn~",
            erAktiv = true,
            kanFjernes = true,
        )
    }
}
