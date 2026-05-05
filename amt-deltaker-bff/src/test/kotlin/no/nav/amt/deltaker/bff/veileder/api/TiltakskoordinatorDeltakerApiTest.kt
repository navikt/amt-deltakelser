package no.nav.amt.deltaker.bff.veileder.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import no.nav.amt.deltaker.bff.deltaker.DeltakerTestUtils.toDeltakerStatusAarsak
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.TestData.lagForslag
import no.nav.amt.deltaker.bff.utils.TestData.lagNavAnsatteForDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagNavAnsatteForHistorikk
import no.nav.amt.deltaker.bff.utils.TestData.lagNavEnheterForHistorikk
import no.nav.amt.deltaker.bff.utils.TestData.leggTilHistorikk
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
import no.nav.amt.deltaker.bff.veileder.api.request.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.request.toInnholdModel
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagOppfolgingsperiode
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.writePolymorphicListAsString
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltakskoordinatorDeltakerApiTest : IntegrationTestBase() {
    @Test
    fun `skal teste tilgangskontroll - har ikke tilgang - returnerer 403`() {
        val deltaker = lagDeltaker(navBruker = lagNavBruker(personident = "1234"))
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )
        every { deltakerRepository.get(any()) } returns Result.success(deltaker)
        every { forslagRepository.get(any()) } returns Result.success(lagForslag())
        every { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns false
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(any()) } returns PersonIdentResponse(deltaker.navBruker.personident)

        withTestApplicationContext { httpClient ->
            val id = UUID.randomUUID()

            val requests: List<suspend () -> HttpResponse> = listOf(
                { httpClient.post("/deltaker/$id/bakgrunnsinformasjon") { createPostRequest(bakgrunnsinformasjonRequest) } },
                { httpClient.post("/deltaker/$id/innhold") { createPostRequest(innholdRequest) } },
                { httpClient.post("/deltaker/$id/deltakelsesmengde") { createPostRequest(deltakelsesmengdeRequest) } },
                { httpClient.post("/deltaker/$id/startdato") { createPostRequest(startdatoRequest) } },
                { httpClient.post("/deltaker/$id/sluttdato") { createPostRequest(sluttdatoRequest) } },
                { httpClient.post("/deltaker/$id/ikke-aktuell") { createPostRequest(ikkeAktuellRequest) } },
                { httpClient.post("/deltaker/$id/forleng") { createPostRequest(forlengDeltakelseRequest) } },
                { httpClient.post("/deltaker/$id/avslutt") { createPostRequest(avsluttDeltakelseRequest) } },
                { httpClient.post("/deltaker/$id/endre-avslutning") { createPostRequest(endreAvslutningRequest) } },
                { httpClient.post("/deltaker/$id") { createPostRequest(deltakerRequest) } },
                { httpClient.get("/deltaker/$id/historikk") { noBodyRequest() } },
                { httpClient.post("/deltaker/$id/reaktiver") { createPostRequest(reaktiverDeltakelseRequest) } },
                { httpClient.post("/forslag/$id/avvis") { createPostRequest(avvisForslagRequest) } },
                { httpClient.post("/deltaker/$id/fjern-oppstartsdato") { createPostRequest(fjernOppstartsdatoRequest) } },
            )

            requests.forEach { requestMethod ->
                requestMethod().status shouldBe HttpStatusCode.Forbidden
            }
        }
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { httpClient ->
            val id = UUID.randomUUID()

            val requests = listOf(
                "POST" to "/deltaker/$id/bakgrunnsinformasjon",
                "POST" to "/deltaker/$id/innhold",
                "POST" to "/deltaker/$id/deltakelsesmengde",
                "POST" to "/deltaker/$id/startdato",
                "POST" to "/deltaker/$id/sluttdato",
                "POST" to "/deltaker/$id/ikke-aktuell",
                "POST" to "/deltaker/$id/forleng",
                "POST" to "/deltaker/$id/avslutt",
                "POST" to "/deltaker/$id/reaktiver",
                "POST" to "/deltaker/$id/fjern-oppstartsdato",
                "POST" to "/deltaker/$id",
                "GET" to "/deltaker/$id/historikk",
                "POST" to "/deltaker/$id/endre-avslutning",
                "POST" to "/forslag/$id/avvis",
            )

            requests.forEach { (method, path) ->
                val response = when (method) {
                    "POST" -> httpClient.post(path) { setBody("foo") }
                    "GET" -> httpClient.get(path)
                    else -> error("Unsupported method $method")
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `oppdater bakgrunnsinformasjon - har tilgang - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))

        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            bakgrunnsinformasjon = bakgrunnsinformasjonRequest.bakgrunnsinformasjon,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(
            deltaker = oppdatertDeltaker,
            mocks = setupMocks(deltaker, oppdatertDeltaker),
        )

        withTestApplicationContext { httpClient ->
            httpClient
                .post("/deltaker/${deltaker.id}/bakgrunnsinformasjon") { createPostRequest(bakgrunnsinformasjonRequest) }
                .apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
                }
        }
    }

    @Test
    fun `oppdater bakgrunnsinformasjon - deltaker har sluttet - returnerer bad request`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusMonths(3),
            ),
            sluttdato = LocalDate.now().minusMonths(3),
        )

        setupMocks(deltaker, null)

        withTestApplicationContext { httpClient ->
            httpClient
                .post("/deltaker/${deltaker.id}/bakgrunnsinformasjon") { createPostRequest(bakgrunnsinformasjonRequest) }
                .apply {
                    status shouldBe HttpStatusCode.BadRequest
                }
        }
    }

    @Test
    fun `oppdater innhold - har tilgang - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(statusType = DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            deltakelsesinnhold = Deltakelsesinnhold("ledetekst", innholdRequest.innhold.toInnholdModel(deltaker)),
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        withTestApplicationContext { httpClient ->
            httpClient
                .post("/deltaker/${deltaker.id}/innhold") {
                    createPostRequest(
                        EndreInnholdRequest(
                            listOf(
                                InnholdsElementRequest(
                                    deltaker.deltakelsesinnhold!!.innhold[0].innholdskode,
                                    null,
                                ),
                            ),
                        ),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
                }
        }
    }

    @Test
    fun `oppdater deltakelsesmengde - har tilgang - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(
            sluttdato = LocalDate.now().plusMonths(3),
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        val oppdatertDeltaker = deltaker.copy(
            dagerPerUke = deltakelsesmengdeRequest.dagerPerUke?.toFloat(),
            deltakelsesprosent = deltakelsesmengdeRequest.deltakelsesprosent?.toFloat(),
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/deltakelsesmengde") { createPostRequest(deltakelsesmengdeRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `oppdater deltakelsesmengde - ingen endring - returnerer BadRequest`() {
        val deltaker = lagDeltaker(
            sluttdato = LocalDate.now().plusMonths(3),
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        setupMocks(deltaker, null)

        withTestApplicationContext { httpClient ->
            httpClient
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
    }

    @Test
    fun `oppdater startdato - har tilgang - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = startdatoRequest.startdato,
            sluttdato = sluttdatoRequest.sluttdato,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/startdato") { createPostRequest(startdatoRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `endre sluttdato - har tilgang, deltaker har status HAR SLUTTET - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusDays(3),
        )
        val oppdatertDeltaker = deltaker.copy(
            sluttdato = sluttdatoRequest.sluttdato,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/sluttdato") { createPostRequest(sluttdatoRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `endre sluttdato - har tilgang, deltaker har status IKKE AKTUELL - feiler`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            sluttdato = LocalDate.now().minusDays(3),
        )
        setupMocks(deltaker, null)

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/sluttdato") { createPostRequest(sluttdatoRequest) }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `ikke aktuell - har tilgang - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(
                DeltakerStatus.Type.IKKE_AKTUELL,
                ikkeAktuellRequest.aarsak.toDeltakerStatusAarsak(),
            ),
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/ikke-aktuell") { createPostRequest(ikkeAktuellRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `endre sluttarsak - har tilgang, deltaker har status HAR SLUTTET - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        )

        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(
                type = DeltakerStatus.Type.HAR_SLUTTET,
                aarsak = sluttarsakRequest.aarsak.toDeltakerStatusAarsak(),
            ),
        )
        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/sluttarsak") { createPostRequest(sluttarsakRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `getDeltaker - har tilgang, deltaker finnes - returnerer deltaker`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            navBruker = lagNavBruker(personident = "1234"),
        )

        val expectedDeltakerResponse = deltakerResponseInTest(deltaker, setupMocks(deltaker, deltaker))

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}") { createPostRequest(deltakerRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
        verify(exactly = 1) { sporbarhetsloggService.sendAuditLog(any(), any()) }
    }

    @Test
    fun `getDeltaker - har annen navBruker i kontekst, deltaker finnes - returnerer badRequest`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            navBruker = lagNavBruker(personident = "4321"),
        )
        setupMocks(deltaker, null)

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}") { createPostRequest(deltakerRequest) }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `getDeltaker - deltaker er importert fra arena - returnerer importertFraArenaDto`() {
        // Arrange
        val innsoktDatoFraArena = LocalDate.now().minusDays(5)
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            navBruker = lagNavBruker(personident = "1234"),
            innsoktDatoFraArena = innsoktDatoFraArena,
        )
        setupMocks(deltaker, null)

        // Act
        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}") { createPostRequest(deltakerRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                val responseText = bodyAsText()
                val deltakerResponse = objectMapper.readValue<DeltakerResponse>(responseText)
                deltakerResponse.importertFraArena?.innsoktDato shouldBe innsoktDatoFraArena
                deltakerResponse.vedtaksinformasjon shouldBe null
            }
        }

        // Assert
        verify(exactly = 1) { sporbarhetsloggService.sendAuditLog(any(), any()) }
    }

    @Test
    fun `getDeltakerHistorikk - har tilgang, deltaker finnes - returnerer historikk`() {
        val deltaker = leggTilHistorikk(lagDeltaker(), 2, 2, 1)
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(deltaker.id) } returns PersonIdentResponse(deltaker.navBruker.personident)

        val historikk = deltaker.getDeltakerHistorikkForVisning()
        val ansatte = lagNavAnsatteForHistorikk(historikk).associateBy { it.id }
        val enheter = lagNavEnheterForHistorikk(historikk).associateBy { it.id }

        val deltakerResponse = lagDeltakerResponse(id = deltaker.id)
        val arrangornavn = deltakerResponse.gjennomforing.arrangor!!.navn
        val oppstartstype = deltakerResponse.gjennomforing.oppstart

        every { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns true
        coEvery { amtDeltakerClient.getDeltakerHistorikkData(any()) } returns DeltakerHistorikkDataResponse(
            historikk = historikk,
            arrangornavn = arrangornavn,
            oppstartstype = oppstartstype,
            pameldingstype = null,
            ansatte = ansatte,
            enheter = enheter,
        )

        withTestApplicationContext { httpClient ->
            httpClient.get("/deltaker/${deltaker.id}/historikk") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
                val res = bodyAsText()
                val json = objectMapper.writePolymorphicListAsString(
                    DeltakerHistorikkResponse.fromModels(
                        models = historikk,
                        arrangornavn = arrangornavn,
                        oppstartstype = oppstartstype,
                        pameldingstype = null,
                        enheter = enheter,
                        ansatte = ansatte,
                    ),
                )
                res shouldBe json
            }
        }
    }

    @Test
    fun `getDeltakerHistorikk - toggle er av - returnerer lokal historikk`() {
        val deltaker = leggTilHistorikk(lagDeltaker(), 2, 2, 1)
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

        val historikk = deltaker.getDeltakerHistorikkForVisning()
        val ansatte = lagNavAnsatteForHistorikk(historikk).associateBy { it.id }
        val enheter = lagNavEnheterForHistorikk(historikk).associateBy { it.id }

        every { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns false
        every { navAnsattService.hentAnsatteForHistorikk(historikk) } returns ansatte
        coEvery { navEnhetService.hentEnheterForHistorikk(historikk) } returns enheter

        withTestApplicationContext { httpClient ->
            httpClient.get("/deltaker/${deltaker.id}/historikk") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
                val res = bodyAsText()
                val json = objectMapper.writePolymorphicListAsString(
                    DeltakerHistorikkResponse.fromModels(
                        models = historikk,
                        arrangornavn = deltaker.deltakerliste.arrangor.getArrangorNavn(),
                        oppstartstype = deltaker.deltakerliste.oppstart,
                        pameldingstype = deltaker.deltakerliste.pameldingstype,
                        enheter = enheter,
                        ansatte = ansatte,
                    ),
                )
                res shouldBe json
            }
        }
    }

    @Test
    fun `forleng - har tilgang - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = forlengDeltakelseRequest.sluttdato.minusDays(3),
        )
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = forlengDeltakelseRequest.sluttdato,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/forleng") { createPostRequest(forlengDeltakelseRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `forleng - har tilgang, ny dato tidligere enn forrige dato - feiler`() {
        val deltaker = lagDeltaker(sluttdato = forlengDeltakelseRequest.sluttdato.plusDays(5))
        setupMocks(deltaker, null)

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/forleng") { createPostRequest(forlengDeltakelseRequest) }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `forleng - har tilgang, har sluttet for mer enn to mnd siden - feiler`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusMonths(3),
            ),
            sluttdato = forlengDeltakelseRequest.sluttdato.minusMonths(3),
        )
        setupMocks(deltaker, null)

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/forleng") { createPostRequest(forlengDeltakelseRequest) }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `forleng - har tilgang, ikke under oppfolging - feiler`() {
        val deltaker = lagDeltaker(
            navBruker = lagNavBruker(
                oppfolgingsperioder = listOf(
                    lagOppfolgingsperiode(
                        startdato = LocalDateTime.now().minusMonths(2),
                        sluttdato = LocalDateTime.now().minusDays(2),
                    ),
                ),
            ),
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = forlengDeltakelseRequest.sluttdato.minusDays(3),
        )
        setupMocks(deltaker, null)

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/forleng") { createPostRequest(forlengDeltakelseRequest) }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `avslutt - har tilgang, har deltatt - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(
                DeltakerStatus.Type.HAR_SLUTTET,
                avsluttDeltakelseRequest.aarsak!!.toDeltakerStatusAarsak(),
            ),
            sluttdato = avsluttDeltakelseRequest.sluttdato,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `avslutt - har tilgang, har deltatt, mangler sluttdato - feiler`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(
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

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequestUtenSluttdato) }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `avslutt - har tilgang, har ikke deltatt - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(
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

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequestIkkeDeltatt) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `avslutt - har tilgang, har ikke deltatt, mer enn 15 dager siden - feiler ikke`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.DELTAR,
                gyldigFra = LocalDateTime.now().minusDays(20),
            ),
        )
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(
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

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequestIkkeDeltatt) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `avslutt - har tilgang, status VENTER PA OPPSTART - feiler`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        setupMocks(deltaker, null)

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequest) }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `endre-avslutning til avbrutt- har tilgang, har fullfort- returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.FULLFORT))
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(
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

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/endre-avslutning") { createPostRequest(endreAvslutningRequestAvbrutt) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `endre-avslutning til fullfort- har tilgang, har avbrutt- returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                DeltakerStatus.Type.AVBRUTT,
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB).toDeltakerStatusAarsak(),
            ),
        )
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(
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

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/endre-avslutning") { createPostRequest(endreAvslutningRequestAvbrutt) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `reaktiver - har tilgang - returnerer oppdatert deltaker`() {
        val deltaker =
            lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL))
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        withTestApplicationContext { httpClient ->
            httpClient
                .post("/deltaker/${deltaker.id}/reaktiver") { createPostRequest(reaktiverDeltakelseRequest) }
                .apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
                }
        }
    }

    @Test
    fun `reaktiver - deltaker har sluttet - returnerer bad request`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusMonths(3),
            ),
            sluttdato = LocalDate.now().minusMonths(1),
        )
        setupMocks(deltaker, null)

        withTestApplicationContext { httpClient ->
            httpClient
                .post("/deltaker/${deltaker.id}/reaktiver") { createPostRequest(reaktiverDeltakelseRequest) }
                .apply {
                    status shouldBe HttpStatusCode.BadRequest
                }
        }
    }

    @Test
    fun `fjern oppstartsdato - har tilgang - returnerer oppdatert deltaker`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().plusWeeks(1),
            sluttdato = LocalDate.now().plusMonths(3),
        )
        val oppdatertDeltaker = deltaker.copy(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )

        val expectedDeltakerResponse = deltakerResponseInTest(oppdatertDeltaker, setupMocks(deltaker, oppdatertDeltaker))

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}/fjern-oppstartsdato") { createPostRequest(fjernOppstartsdatoRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
            }
        }
    }

    @Test
    fun `avvis forslag - har tilgang - returnerer oppdatert deltaker`() {
        // Arrange
        val deltaker = lagDeltaker()
        val forslag = lagForslag(deltakerId = deltaker.id)

        coEvery {
            forslagService.avvisForslag(forslag, any(), any(), any())
        } just Runs

        every { forslagRepository.get(forslag.id) } returns Result.success(forslag)

        val expectedDeltakerResponse = deltakerResponseInTest(deltaker, setupMocks(deltaker, deltaker))

        // Act & Assert
        withTestApplicationContext { httpClient ->
            httpClient
                .post("/forslag/${forslag.id}/avvis") { createPostRequest(avvisForslagRequest) }
                .apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expectedDeltakerResponse)
                }
        }
    }

    private val deltakerRequest = DeltakerRequest("1234")
    private val bakgrunnsinformasjonRequest = EndreBakgrunnsinformasjonRequest("Oppdatert bakgrunnsinformasjon")
    private val innholdRequest = EndreInnholdRequest(listOf(InnholdsElementRequest("annet", "beskrivelse")))
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
        every { sporbarhetsloggService.sendAuditLog(any(), any()) } just Runs
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        every { deltakerRepository.getMany(deltaker.navBruker.personident, deltaker.deltakerliste.id) } returns listOf(deltaker)
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        every { forslagRepository.getForDeltaker(deltaker.id) } returns forslag
        every { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns false
        every { commonUnleashToggle.erKometMasterForTiltakstype(any<String>()) } returns true
        every { commonUnleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(deltaker.id) } returns PersonIdentResponse(deltaker.navBruker.personident)

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
        val ansatte = lagNavAnsatteForDeltaker(deltaker).associateBy { it.id }
        val enhet = deltaker.vedtaksinformasjon?.let { lagNavEnhet(id = it.sistEndretAvEnhet) }
        val enheter = lagNavEnheterForHistorikk(deltaker.historikk).associateBy { it.id }

        every { navAnsattService.hentAnsatteForDeltaker(deltaker) } returns ansatte
        enhet?.let { every { navEnhetService.hentEnhet(it.id) } returns it }
        coEvery { navEnhetService.hentEnheterForHistorikk(any()) } returns enheter

        return Pair(ansatte, enhet)
    }

    companion object {
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
