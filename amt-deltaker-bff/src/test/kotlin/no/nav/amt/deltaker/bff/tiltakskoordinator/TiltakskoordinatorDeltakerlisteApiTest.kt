package no.nav.amt.deltaker.bff.tiltakskoordinator

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import no.nav.amt.deltaker.bff.deltaker.api.utils.createPostRequest
import no.nav.amt.deltaker.bff.deltaker.api.utils.createPostTiltakskoordinatorRequest
import no.nav.amt.deltaker.bff.deltaker.api.utils.noBodyRequest
import no.nav.amt.deltaker.bff.deltaker.api.utils.noBodyTiltakskoordinatorRequest
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteStengtException
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.response.DeltakerlisteResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.toDeltakerResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.toResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.model.Tiltakskoordinator
import no.nav.amt.deltaker.bff.utils.RouteTestBase
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.bff.utils.data.TestData.lagTiltakskoordinatorDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagTiltakskoordinatorTilgang
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import org.junit.jupiter.api.Test
import java.util.UUID

class TiltakskoordinatorDeltakerlisteApiTest : RouteTestBase() {
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
    fun `get deltakerliste - liste finnes ikke - returnerer 404`() {
        every {
            deltakerlisteService.get(any())
        } returns Result.failure(NoSuchElementException())

        val response = withTestApplicationContext { client ->
            client.get("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}") {
                noBodyTiltakskoordinatorRequest()
            }
        }

        response.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `get deltakerliste - liste finnes - returnerer 200 og liste`() {
        val expectedResponse = deltakerlisteInTest.toResponse(listOf(tiltakskoordinatorInTest))

        every { deltakerlisteService.get(deltakerlisteInTest.id) } returns Result.success(deltakerlisteInTest)
        every {
            tiltakskoordinatorTilgangRepository.hentKoordinatorer(
                deltakerlisteId = any(),
                paaloggetNavAnsattId = any(),
            )
        } returns listOf(tiltakskoordinatorInTest)

        withTestApplicationContext { client ->
            val response = client.get("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}") {
                noBodyTiltakskoordinatorRequest()
            }

            response.status shouldBe HttpStatusCode.OK

            val actualResponse = response.body<DeltakerlisteResponse>()
            actualResponse shouldBe expectedResponse
        }
    }

    @Test
    fun `get deltakere - mangler tilgang til deltakerliste - returnerer 403`() {
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
        coEvery { tilgangskontrollService.verifiserTiltakskoordinatorTilgang(any(), any()) } throws AuthorizationException("")

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
        coEvery { tiltakskoordinatorService.hentDeltakereForDeltakerliste(any()) } returns emptyList()

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

        val deltakere = (0..5).map { lagTiltakskoordinatorDeltaker(deltakerliste = deltakerlisteInTest) }
        val navEnheter = deltakere
            .mapNotNull { it.navBruker.navEnhetId }
            .distinct()
            .map { lagNavEnhet(it) }
            .associateBy { it.id }

        val expectedResponse = deltakere.map { deltaker ->
            deltaker.toDeltakerResponse(true)
        }

        every { navEnhetService.hentEnheter(any()) } returns navEnheter
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
        coEvery { tiltakskoordinatorService.hentDeltakereForDeltakerliste(deltakerlisteInTest.id) } returns deltakere

        deltakere.forEach {
            every { tilgangskontrollService.harKoordinatorTilgangTilPerson(any(), it.navBruker) } returns true
        }

        withTestApplicationContext { client ->
            val response = client.get("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") {
                noBodyTiltakskoordinatorRequest()
            }

            response.status shouldBe HttpStatusCode.OK

            val actualResponse = response.body<List<DeltakerResponse>>()
            actualResponse shouldBe expectedResponse
        }
    }

    @Test
    fun `legg til tilgang - har ikke tilgang fra for - returnerer 200`() {
        coEvery {
            tilgangskontrollService.leggTilTiltakskoordinatorTilgang(
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
            tilgangskontrollService.leggTilTiltakskoordinatorTilgang(
                any(),
                any(),
            )
        } returns Result.failure(IllegalArgumentException())

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
        coEvery { tilgangskontrollService.tilgangTilDeltakereGuard(any(), any(), any()) } throws AuthorizationException("")

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

        coEvery { tiltakskoordinatorService.hentDeltakereForDeltakerliste(any()) } returns emptyList()
        coEvery { tilgangskontrollService.tilgangTilDeltakereGuard(any(), any(), any()) } throws NoSuchElementException()

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

        coEvery { tilgangskontrollService.verifiserTiltakskoordinatorTilgang(any(), any()) } returns Unit
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
        coEvery { tilgangskontrollService.verifiserTiltakskoordinatorTilgang(any(), any()) } returns Unit
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
