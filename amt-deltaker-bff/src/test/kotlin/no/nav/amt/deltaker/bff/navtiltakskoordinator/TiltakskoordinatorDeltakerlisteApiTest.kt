package no.nav.amt.deltaker.bff.navtiltakskoordinator

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteStengtException
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.AvslagRequest
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
import no.nav.amt.internapi.deltaker.response.PaginatedResult
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerlisteFilterCountsResponse
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorDeltakerIListeResponse
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class TiltakskoordinatorDeltakerlisteApiTest : IntegrationTestBase() {
    override val tiltakskoordinatorResponseBuilder = ResponseBuilder(ulestHendelseRepository)

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.get("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
            client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere").status shouldBe HttpStatusCode.Unauthorized
            client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere/status-counts").status shouldBe
                HttpStatusCode.Unauthorized
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
        every { deltakerlisteRepository.get(deltakerlisteInTest.id) } returns Result.success(deltakerlisteInTest)

        withTestApplicationContext { client ->
            client
                .get("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}") { noBodyRequest() }
                .apply { status shouldBe HttpStatusCode.Unauthorized }

            client
                .post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") { noBodyRequest() }
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
            tiltakskoordinatorClient.getGjennomforing(any())
        } throws NoSuchElementException()
        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(any<String>()) } returns lagNavAnsatt()
        every { tiltakskoordinatorTilgangRepository.hentKoordinatorer(any(), any()) } returns emptyList()

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
        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(any<String>()) } returns lagNavAnsatt()
        every { deltakerlisteRepository.get(deltakerlisteInTest.id) } returns Result.success(deltakerlisteInTest)
        coEvery { tiltakskoordinatorClient.getGjennomforing(deltakerlisteInTest.id) } returns gjennomforing

        every {
            tiltakskoordinatorTilgangRepository.hentKoordinatorer(
                deltakerlisteId = any(),
                paaloggetNavAnsattId = any(),
            )
        } returns listOf(tiltakskoordinatorInTest)

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

    @Nested
    inner class PostDeltakereTests {
        @Test
        fun `post deltakere - mangler tilgang til deltakerliste - returnerer 403`() {
            every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
            coEvery {
                selfServiceTilgangskontrollService.verifiserTiltakskoordinatorTilgang(
                    any(),
                    any(),
                )
            } throws AuthorizationException("")

            val response = withTestApplicationContext { client ->
                client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") {
                    noBodyTiltakskoordinatorRequest()
                }
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }

        @Test
        fun `post deltakere - deltakerliste finnes ikke - returnerer 404`() {
            mockTilgangTilDeltakerliste()

            every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(any()) } throws NoSuchElementException()

            val response = withTestApplicationContext { client ->
                client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere") {
                    noBodyTiltakskoordinatorRequest()
                }
            }

            response.status shouldBe HttpStatusCode.NotFound
        }

        @Test
        fun `post deltakere - deltakerliste er stengt - returnerer 410`() {
            mockTilgangTilDeltakerliste()

            every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(any()) } throws DeltakerlisteStengtException()

            val response = withTestApplicationContext { client ->
                client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere") {
                    noBodyTiltakskoordinatorRequest()
                }
            }

            response.status shouldBe HttpStatusCode.Gone
        }

        @Test
        fun `post deltakere - deltakerliste finnes - returnerer liste med deltakere`() {
            mockTilgangTilDeltakerliste()

            val deltakere = (0..5)
                .map { lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR)) }
            val deltakereResponse = tiltakskoordinatorDeltakereResponse(deltakere)

            coEvery { tiltakskoordinatorClient.getDeltakereForGjennomforing(deltakereRequest()) } returns deltakereResponse
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
                val response = client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") {
                    createPostTiltakskoordinatorRequest(deltakereRequest())
                }

                response.status shouldBe HttpStatusCode.OK

                val actualResponse = response.body<List<DeltakerResponse>>()
                actualResponse.size shouldBe deltakere.size
                actualResponse.map { it.id } shouldBe deltakere.map { it.id }
            }
        }

        @Test
        fun `post deltakere - returnerer alle deltakere fra amt-deltaker uten ekstra filtering`() {
            mockTilgangTilDeltakerliste()

            val deltaker1 = lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val deltaker2 = lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))

            coEvery { tiltakskoordinatorClient.getDeltakereForGjennomforing(deltakereRequest()) } returns
                tiltakskoordinatorDeltakereResponse(listOf(deltaker1, deltaker2))
            every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
            every { ulestHendelseRepository.getForDeltakere(any()) } returns emptyMap()
            every {
                tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(any(), any(), any())
            } returns true

            withTestApplicationContext { client ->
                val response = client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") {
                    createPostTiltakskoordinatorRequest(deltakereRequest())
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<DeltakerResponse>>()
                body.size shouldBe 2
                body.map { it.id }.toSet() shouldBe setOf(deltaker1.id, deltaker2.id)
            }
        }

        @Test
        fun `post deltakere - mangler tilgang til skjermet person - kanSeInnbyggersNavn er false`() {
            mockTilgangTilDeltakerliste()

            val skjermetDeltaker = lagTiltakskoordinatorDeltakerResponse(
                navBruker = lagTiltakskoordinatorNavBrukerResponse(erSkjermet = true),
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            coEvery { tiltakskoordinatorClient.getDeltakereForGjennomforing(deltakereRequest()) } returns
                tiltakskoordinatorDeltakereResponse(listOf(skjermetDeltaker))
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
                val response = client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") {
                    createPostTiltakskoordinatorRequest(deltakereRequest())
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<DeltakerResponse>>()
                body.size shouldBe 1
                // navnefelt skal være maskert når kanSeInnbyggersNavn=false (skjermet person)
                body.single().fornavn shouldBe DeltakerResponseUtils.SKJERMET_PERSON_PLACEHOLDER_NAVN
            }
        }

        @Test
        fun `post deltakere - deltakerliste finnes - returnerer paginerte deltakere`() {
            mockTilgangTilDeltakerliste()

            val request = filteredDeltakereRequest()
            val deltakere = (0..1)
                .map { lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR)) }

            val deltakereResponse = tiltakskoordinatorDeltakereResponse(
                deltakere = deltakere,
                totalCount = 6,
            )

            coEvery { tiltakskoordinatorClient.getDeltakereForGjennomforing(request) } returns deltakereResponse
            every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
            every { ulestHendelseRepository.getForDeltakere(any()) } returns emptyMap()
            every {
                tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(any(), any(), any())
            } returns true

            withTestApplicationContext { client ->
                val response = client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere") {
                    createPostTiltakskoordinatorRequest(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<DeltakerResponse>>()
                body.map { it.id } shouldBe deltakere.map { it.id }
            }

            coVerify(exactly = 1) { tiltakskoordinatorClient.getDeltakereForGjennomforing(request) }
        }
    }

    @Test
    fun `post status-counts - mangler tilgang til deltakerliste - returnerer 403`() {
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest
        coEvery { selfServiceTilgangskontrollService.verifiserTiltakskoordinatorTilgang(any(), any()) } throws AuthorizationException("")

        val response = withTestApplicationContext { client ->
            client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/status-counts") {
                createPostTiltakskoordinatorRequest(filteredDeltakereRequest())
            }
        }

        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `post status-counts - deltakerliste finnes - returnerer counts per status`() {
        mockTilgangTilDeltakerliste()

        val request = filteredDeltakereRequest(
            gjennomforingId = UUID.randomUUID(),
            statuser = setOf(DeltakerStatus.Type.DELTAR, DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val expectedRequest = request.copy(gjennomforingId = deltakerlisteInTest.id)

        val expectedResponse = DeltakerlisteFilterCountsResponse(
            statusCounts = mapOf(
                DeltakerStatus.Type.DELTAR to 2,
                DeltakerStatus.Type.VENTER_PA_OPPSTART to 1,
            ),
            handlingCounts = emptyMap(),
        )

        coEvery { tiltakskoordinatorClient.getDeltakereCountPerStatus(expectedRequest) } returns expectedResponse
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest

        withTestApplicationContext { client ->
            val response = client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/status-counts") {
                createPostTiltakskoordinatorRequest(request)
            }

            response.status shouldBe HttpStatusCode.OK
            response.body<DeltakerlisteFilterCountsResponse>() shouldBe expectedResponse
        }

        coVerify(exactly = 1) { tiltakskoordinatorClient.getDeltakereCountPerStatus(expectedRequest) }
    }

    @Test
    fun `post status-counts - tomme statuser - returnerer 400`() {
        mockTilgangTilDeltakerliste()
        every { deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteInTest.id) } returns deltakerlisteInTest

        val response = withTestApplicationContext { client ->
            client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/status-counts") {
                createPostTiltakskoordinatorRequest(filteredDeltakereRequest())
            }
        }

        response.status shouldBe HttpStatusCode.BadRequest
        coVerify(exactly = 0) { tiltakskoordinatorClient.getDeltakereCountPerStatus(any()) }
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

    @Nested
    inner class TildelPlassTests {
        @Test
        fun `post tildel-plass - mangler tilgang - returnerer 403`() {
            coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(any(), any()) } throws
                AuthorizationException("")

            val response = withTestApplicationContext { client ->
                client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/tildel-plass") {
                    createPostTiltakskoordinatorRequest(listOf(UUID.randomUUID()))
                }
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }

        @Test
        fun `post tildel-plass - har tilgang - returnerer 200 med mappede deltakere`() {
            val deltaker = lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val oppdateringResponse = DeltakerOppdateringResponse(deltaker = deltaker, feilkode = null)

            coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(any(), any()) } returns Unit
            coEvery {
                tiltakskoordinatorClient.tildelPlass(
                    gjennomforingId = deltakerlisteInTest.id,
                    deltakerIder = listOf(deltaker.id),
                    endretAv = any(),
                )
            } returns listOf(oppdateringResponse)
            every { ulestHendelseRepository.getForDeltakere(any()) } returns emptyMap()
            every {
                tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(any(), any(), any())
            } returns true

            withTestApplicationContext { client ->
                val response = client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/tildel-plass") {
                    createPostTiltakskoordinatorRequest(listOf(deltaker.id))
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<DeltakerResponse>>()
                body shouldHaveSize 1
                body.single().id shouldBe deltaker.id
                body.single().feilkode shouldBe null
                Unit
            }
        }

        @Test
        fun `post tildel-plass - feilkode fra backend - propageres til frontend-respons`() {
            val deltaker = lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN))
            val oppdateringResponse = DeltakerOppdateringResponse(
                deltaker = deltaker,
                feilkode = DeltakerOppdateringFeilkode.UGYLDIG_STATE,
            )

            coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(any(), any()) } returns Unit
            coEvery {
                tiltakskoordinatorClient.tildelPlass(any(), any(), any())
            } returns listOf(oppdateringResponse)
            every { ulestHendelseRepository.getForDeltakere(any()) } returns emptyMap()
            every {
                tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(any(), any(), any())
            } returns true

            withTestApplicationContext { client ->
                val response = client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/tildel-plass") {
                    createPostTiltakskoordinatorRequest(listOf(deltaker.id))
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<DeltakerResponse>>()
                body.single().feilkode shouldBe DeltakerOppdateringFeilkode.UGYLDIG_STATE
                Unit
            }
        }
    }

    @Nested
    inner class SettPaaVentelisteTests {
        @Test
        fun `post sett-paa-venteliste - mangler tilgang - returnerer 403`() {
            coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(any(), any()) } throws
                AuthorizationException("")

            val response = withTestApplicationContext { client ->
                client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/sett-paa-venteliste") {
                    createPostTiltakskoordinatorRequest(listOf(UUID.randomUUID()))
                }
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }

        @Test
        fun `post sett-paa-venteliste - har tilgang - returnerer 200 med mappede deltakere`() {
            val deltaker = lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val oppdateringResponse = DeltakerOppdateringResponse(deltaker = deltaker, feilkode = null)

            coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(any(), any()) } returns Unit
            coEvery {
                tiltakskoordinatorClient.settPaaVenteliste(
                    gjennomforingId = deltakerlisteInTest.id,
                    deltakerIder = listOf(deltaker.id),
                    endretAv = any(),
                )
            } returns listOf(oppdateringResponse)
            every { ulestHendelseRepository.getForDeltakere(any()) } returns emptyMap()
            every {
                tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(any(), any(), any())
            } returns true

            withTestApplicationContext { client ->
                val response =
                    client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/sett-paa-venteliste") {
                        createPostTiltakskoordinatorRequest(listOf(deltaker.id))
                    }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<DeltakerResponse>>()
                body shouldHaveSize 1
                body.single().id shouldBe deltaker.id
            }
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
    }

    @Nested
    inner class DelMedArrangorTests {
        @Test
        fun `post del-med-arrangor - mangler tilgang til deltakerliste - returnerer 403`() {
            coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(any(), any()) } throws
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
            coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(any(), any()) } throws
                NoSuchElementException()

            val response = withTestApplicationContext { client ->
                client.post("/tiltakskoordinator/deltakerliste/${UUID.randomUUID()}/deltakere/del-med-arrangor") {
                    createPostTiltakskoordinatorRequest(listOf(UUID.randomUUID()))
                }
            }

            response.status shouldBe HttpStatusCode.NotFound
        }

        @Test
        fun `post del-med-arrangor - har tilgang - returnerer 200 med mappede deltakere`() {
            val deltaker = lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val oppdateringResponse = DeltakerOppdateringResponse(deltaker = deltaker, feilkode = null)

            coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(any(), any()) } returns Unit
            coEvery {
                tiltakskoordinatorClient.delMedArrangor(
                    gjennomforingId = deltakerlisteInTest.id,
                    deltakerIder = listOf(deltaker.id),
                    endretAv = any(),
                )
            } returns listOf(oppdateringResponse)
            every { ulestHendelseRepository.getForDeltakere(any()) } returns emptyMap()
            every {
                tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(any(), any(), any())
            } returns true

            withTestApplicationContext { client ->
                val response =
                    client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/del-med-arrangor") {
                        createPostTiltakskoordinatorRequest(listOf(deltaker.id))
                    }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<DeltakerResponse>>()
                body shouldHaveSize 1
                body.single().id shouldBe deltaker.id
            }
        }
    }

    @Nested
    inner class GiAvslagTests {
        @Test
        fun `post gi-avslag - mangler tilgang - returnerer 403`() {
            coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(any(), any()) } throws
                AuthorizationException("")

            val response = withTestApplicationContext { client ->
                client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/gi-avslag") {
                    createPostTiltakskoordinatorRequest(
                        AvslagRequest(
                            deltakerId = UUID.randomUUID(),
                            aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(
                                EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT,
                                null,
                            ),
                            begrunnelse = null,
                        ),
                    )
                }
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }

        @Test
        fun `post gi-avslag - har tilgang - returnerer 200 med mappet deltaker`() {
            val deltaker = lagTiltakskoordinatorDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL))
            val oppdateringResponse = DeltakerOppdateringResponse(deltaker = deltaker, feilkode = null)
            val avslagRequest = AvslagRequest(
                deltakerId = deltaker.id,
                aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(
                    EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT,
                    null,
                ),
                begrunnelse = null,
            )

            coEvery { tiltakskoordinatorTilgangskontrollService.tilgangTilGjennomforingGuard(any(), any()) } returns Unit
            coEvery {
                tiltakskoordinatorClient.giAvslag(
                    gjennomforingId = deltakerlisteInTest.id,
                    avslagRequest = avslagRequest,
                    endretAv = any(),
                )
            } returns oppdateringResponse
            every { ulestHendelseRepository.getForDeltakere(any()) } returns emptyMap()
            every {
                tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(any(), any(), any())
            } returns true

            withTestApplicationContext { client ->
                val response =
                    client.post("/tiltakskoordinator/deltakerliste/${deltakerlisteInTest.id}/deltakere/gi-avslag") {
                        createPostTiltakskoordinatorRequest(avslagRequest)
                    }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<DeltakerResponse>()
                body.id shouldBe deltaker.id
            }
        }
    }

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

        private fun deltakereRequest() = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = deltakerlisteInTest.id,
        )

        private fun filteredDeltakereRequest(
            gjennomforingId: UUID = deltakerlisteInTest.id,
            statuser: Set<DeltakerStatus.Type> = emptySet(),
        ) = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = gjennomforingId,
            statuser = statuser,
        )

        private fun tiltakskoordinatorDeltakereResponse(
            deltakere: List<TiltakskoordinatorDeltakerIListeResponse>,
            totalCount: Int = deltakere.size,
            pageSize: Int = deltakere.size,
        ) = PaginatedResult(
            totalCount = totalCount,
            pageSize = pageSize,
            data = deltakere,
        )
    }
}
