package no.nav.amt.deltaker.bff.veileder.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.deltaker.DeltakerTestUtils.toDeltakerStatusAarsak
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerModel
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerOld
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.TestData.lagForslag
import no.nav.amt.deltaker.bff.utils.TestData.lagNavAnsatteForHistorikk
import no.nav.amt.deltaker.bff.utils.TestData.lagNavEnheterForHistorikk
import no.nav.amt.deltaker.bff.utils.TestData.leggTilHistorikk
import no.nav.amt.deltaker.bff.veileder.api.request.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.request.AvvisForslagRequest
import no.nav.amt.deltaker.bff.veileder.api.request.DeltakerRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreAvslutningRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreBakgrunnsinformasjonRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreDeltakelsesmengdeRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreInnholdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreOpplaringKategoriseringRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndrePrisinfoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreSluttarsakRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreSluttdatoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreStartdatoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.FjernOppstartsdatoRequest
import no.nav.amt.deltaker.bff.veileder.api.request.ForlengDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.request.IkkeAktuellRequest
import no.nav.amt.deltaker.bff.veileder.api.request.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerHistorikkResponse
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.noBodyRequest
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.deltaker.request.OpplaringKategoriseringValgRequest
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.writePolymorphicListAsString
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class VeilederApiTest : IntegrationTestBase() {
    // ---- Autentisering ----

    @Test
    fun `alle endepunkter - mangler token - returnerer 401`() {
        withTestApplicationContext { httpClient ->
            val id = UUID.randomUUID()

            val requests = listOf(
                "POST" to "/deltaker/$id",
                "GET" to "/deltaker/$id/historikk",
                "POST" to "/deltaker/$id/endre-prisinfo",
                "POST" to "/deltaker/$id/endre-innhold-kodeverk",
                "POST" to "/deltaker/$id/bakgrunnsinformasjon",
                "POST" to "/deltaker/$id/innhold",
                "POST" to "/deltaker/$id/deltakelsesmengde",
                "POST" to "/deltaker/$id/startdato",
                "POST" to "/deltaker/$id/sluttdato",
                "POST" to "/deltaker/$id/sluttarsak",
                "POST" to "/deltaker/$id/ikke-aktuell",
                "POST" to "/deltaker/$id/reaktiver",
                "POST" to "/deltaker/$id/avslutt",
                "POST" to "/deltaker/$id/endre-avslutning",
                "POST" to "/deltaker/$id/forleng",
                "POST" to "/deltaker/$id/fjern-oppstartsdato",
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

    // ---- Tilgangskontroll ----

    @Test
    fun `alle endepunkter - har ikke tilgang - returnerer 403`() {
        val deltaker = lagDeltakerOld(navBruker = lagNavBruker(personident = "1234"))
        every { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns true
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Deny("Ikke tilgang", ""))
        every { deltakerRepository.get(any()) } returns Result.success(deltaker)
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(any()) } returns
            PersonIdentResponse(deltaker.navBruker.personident).personident
        coEvery { amtDeltakerClient.getPersonidentForForslag(any()) } returns
            PersonIdentResponse(deltaker.navBruker.personident).personident

        coEvery { amtDeltakerClient.getDeltaker(any()) } returns lagDeltakerResponse()

        withTestApplicationContext { httpClient ->
            val id = UUID.randomUUID()

            val requests: List<suspend () -> HttpResponse> = listOf(
                { httpClient.post("/deltaker/$id") { createPostRequest(deltakerRequest) } },
                { httpClient.get("/deltaker/$id/historikk") { noBodyRequest() } },
                { httpClient.post("/deltaker/$id/endre-prisinfo") { createPostRequest(endrePrisinfoRequest) } },
                { httpClient.post("/deltaker/$id/endre-innhold-kodeverk") { createPostRequest(endreOpplaringKategoriseringRequest) } },
                { httpClient.post("/deltaker/$id/bakgrunnsinformasjon") { createPostRequest(bakgrunnsinformasjonRequest) } },
                { httpClient.post("/deltaker/$id/innhold") { createPostRequest(innholdRequest) } },
                { httpClient.post("/deltaker/$id/deltakelsesmengde") { createPostRequest(deltakelsesmengdeRequest) } },
                { httpClient.post("/deltaker/$id/startdato") { createPostRequest(startdatoRequest) } },
                { httpClient.post("/deltaker/$id/sluttdato") { createPostRequest(sluttdatoRequest) } },
                { httpClient.post("/deltaker/$id/sluttarsak") { createPostRequest(sluttarsakRequest) } },
                { httpClient.post("/deltaker/$id/ikke-aktuell") { createPostRequest(ikkeAktuellRequest) } },
                { httpClient.post("/deltaker/$id/reaktiver") { createPostRequest(reaktiverDeltakelseRequest) } },
                { httpClient.post("/deltaker/$id/avslutt") { createPostRequest(avsluttDeltakelseRequest) } },
                { httpClient.post("/deltaker/$id/endre-avslutning") { createPostRequest(endreAvslutningRequest) } },
                { httpClient.post("/deltaker/$id/forleng") { createPostRequest(forlengDeltakelseRequest) } },
                { httpClient.post("/deltaker/$id/fjern-oppstartsdato") { createPostRequest(fjernOppstartsdatoRequest) } },
                { httpClient.post("/forslag/$id/avvis") { createPostRequest(avvisForslagRequest) } },
            )

            requests.forEach { requestMethod ->
                requestMethod().status shouldBe HttpStatusCode.Forbidden
            }
        }
    }

    // ---- getDeltaker ----

    @Test
    fun `getDeltaker - har tilgang - returnerer deltaker fra amt-deltaker`() {
        val deltakerResponse = lagDeltakerResponse()
        val deltakerId = deltakerResponse.id
        val personident = "1234"

        coEvery { amtDeltakerClient.getPersonidentForDeltaker(deltakerId) } returns PersonIdentResponse(personident).personident
        coEvery { amtDeltakerClient.getDeltaker(deltakerId) } returns deltakerResponse
        every { sporbarhetsloggService.sendAuditLog(any(), any()) } just Runs

        val expected = DeltakerResponse.fromDeltakerModel(ModelMapper.toDeltaker(deltakerResponse))

        withTestApplicationContext { httpClient ->
            httpClient
                .post("/deltaker/$deltakerId") {
                    createPostRequest(DeltakerRequest(personident))
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
        }
    }

    @Test
    fun `getDeltaker - feil personident i kontekst - returnerer 400`() {
        val deltaker = lagDeltakerOld(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            navBruker = lagNavBruker(personident = "4321"),
        )
        setupMocks(deltaker)

        withTestApplicationContext { httpClient ->
            httpClient.post("/deltaker/${deltaker.id}") { createPostRequest(deltakerRequest) }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    // ---- historikk ----

    @Test
    fun `getDeltakerHistorikk - toggle på - returnerer historikk fra amt-deltaker`() {
        val deltaker = lagDeltakerModel()
        val historikk = leggTilHistorikk(deltaker, 2, 2, 1)
        val ansatte = lagNavAnsatteForHistorikk(historikk).associateBy { it.id }
        val enheter = lagNavEnheterForHistorikk(historikk).associateBy { it.id }

        val deltakerResponse = lagDeltakerResponse()
        val arrangornavn = deltakerResponse.gjennomforing.arrangor!!.navn
        val oppstartstype = deltakerResponse.gjennomforing.oppstart

        coEvery { amtDeltakerClient.getPersonidentForDeltaker(deltaker.id) } returns
            PersonIdentResponse(deltaker.navBruker.personident).personident
        coEvery { amtDeltakerClient.getDeltakerHistorikkData(deltaker.id) } returns DeltakerHistorikkDataResponse(
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
                val expected = objectMapper.writePolymorphicListAsString(
                    DeltakerHistorikkResponse.fromModels(
                        models = historikk,
                        arrangornavn = arrangornavn,
                        oppstartstype = oppstartstype,
                        pameldingstype = null,
                        enheter = enheter,
                        ansatte = ansatte,
                    ),
                )
                bodyAsText() shouldBe expected
            }
        }
    }

    @Nested
    inner class DeltakerEndringer {
        @BeforeEach
        fun setup() {
            every { commonUnleashToggle.prioriterSynkronKommunikasjon() } returns true
        }

        fun setupMocksLocal(
            deltaker: Deltaker,
            oppdatert: Deltaker? = null,
            gjennomforingType: GjennomforingType = GjennomforingType.Gruppe,
        ): DeltakerResponse {
            val foerResponse = lagDeltakerResponse(deltaker)
                .copy(gjennomforing = lagDeltakerResponse(deltaker).gjennomforing.copy(type = gjennomforingType))
            val etterResponse = lagDeltakerResponse(oppdatert ?: deltaker)
                .copy(gjennomforing = lagDeltakerResponse(oppdatert ?: deltaker).gjennomforing.copy(type = gjennomforingType))

            // handleEndring henter deltakeren via amtDeltakerClient både før (til validering)
            // og etter oppdateringen. `returnsMany` gir forskjellig svar på de to kallene.
            coEvery { amtDeltakerClient.getDeltaker(deltaker.id) } returnsMany listOf(foerResponse)
            coEvery { amtDeltakerClient.postEndreDeltaker(deltaker.id, any()) } returns etterResponse
            coEvery { amtDeltakerClient.avvisForslag(any(), any()) } returns etterResponse

            // Koden kjøres så mockene må settes opp men det er ikke noe som brukes for responsen når toggele er på
            setupMocks(deltaker)
            return DeltakerResponse.fromDeltakerModel(ModelMapper.toDeltaker(etterResponse))
        }

        @Test
        fun `oppdater bakgrunnsinformasjon - har tilgang - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            val oppdatert = deltaker.copy(bakgrunnsinformasjon = bakgrunnsinformasjonRequest.bakgrunnsinformasjon)
            val expected = setupMocksLocal(deltaker, oppdatert)

            withTestApplicationContext { httpClient ->
                httpClient
                    .post("/deltaker/${deltaker.id}/bakgrunnsinformasjon") {
                        createPostRequest(bakgrunnsinformasjonRequest)
                    }.apply {
                        status shouldBe HttpStatusCode.OK
                        bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                    }
            }
        }

        @Nested
        inner class OppdaterOpplaringKategoriseringTests {
            @Test
            fun `oppdater opplæringskategorisering - har tilgang - returnerer deltaker`() {
                val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
                val expected = setupMocksLocal(
                    deltaker = deltaker,
                    oppdatert = deltaker,
                    gjennomforingType = GjennomforingType.Enkeltplass,
                )

                withTestApplicationContext { httpClient ->
                    httpClient
                        .post("/deltaker/${deltaker.id}/endre-innhold-kodeverk") {
                            createPostRequest(endreOpplaringKategoriseringRequest)
                        }.apply {
                            status shouldBe HttpStatusCode.OK
                            bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                        }
                }
            }

            @Test
            fun `oppdater opplæringskategorisering - status utkast til påmelding - returnerer 400`() {
                val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING))
                setupMocks(deltaker)
                coEvery { amtDeltakerClient.getDeltaker(deltaker.id) } returns lagDeltakerResponse(deltaker)
                    .copy(gjennomforing = lagDeltakerResponse(deltaker).gjennomforing.copy(type = GjennomforingType.Enkeltplass))

                withTestApplicationContext { httpClient ->
                    httpClient
                        .post("/deltaker/${deltaker.id}/endre-innhold-kodeverk") {
                            createPostRequest(endreOpplaringKategoriseringRequest)
                        }.apply {
                            status shouldBe HttpStatusCode.BadRequest
                            bodyAsText().contains(
                                "Kan ikke endre opplæringskategorisering for deltaker med status UTKAST_TIL_PAMELDING",
                            ) shouldBe
                                true
                        }
                }
            }

            @Test
            fun `oppdater opplæringskategorisering - status kladd - returnerer 400`() {
                val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))
                setupMocks(deltaker)
                coEvery { amtDeltakerClient.getDeltaker(deltaker.id) } returns lagDeltakerResponse(deltaker)
                    .copy(gjennomforing = lagDeltakerResponse(deltaker).gjennomforing.copy(type = GjennomforingType.Enkeltplass))

                withTestApplicationContext { httpClient ->
                    httpClient
                        .post("/deltaker/${deltaker.id}/endre-innhold-kodeverk") {
                            createPostRequest(endreOpplaringKategoriseringRequest)
                        }.apply {
                            status shouldBe HttpStatusCode.BadRequest
                            bodyAsText().contains("Kan ikke endre opplæringskategorisering for deltaker med status KLADD") shouldBe true
                        }
                }
            }

            @Test
            fun `oppdater opplæringskategorisering - gruppegjennomføring - returnerer 400`() {
                val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN))
                setupMocks(deltaker)
                coEvery { amtDeltakerClient.getDeltaker(deltaker.id) } returns lagDeltakerResponse(deltaker)

                withTestApplicationContext { httpClient ->
                    httpClient
                        .post("/deltaker/${deltaker.id}/endre-innhold-kodeverk") {
                            createPostRequest(endreOpplaringKategoriseringRequest)
                        }.apply {
                            status shouldBe HttpStatusCode.BadRequest
                            bodyAsText().contains(
                                "Kan ikke endre opplæringskategorisering for deltakere som ikke er på enkeltplass",
                            ) shouldBe
                                true
                        }
                }
            }

            @Test
            fun `oppdater opplæringskategorisering - tom beskrivelse - returnerer 400`() {
                val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN))
                setupMocks(deltaker)
                coEvery { amtDeltakerClient.getDeltaker(deltaker.id) } returns lagDeltakerResponse(deltaker)
                    .copy(gjennomforing = lagDeltakerResponse(deltaker).gjennomforing.copy(type = GjennomforingType.Enkeltplass))

                val request = endreOpplaringKategoriseringRequest.copy(beskrivelse = "  ")

                withTestApplicationContext { httpClient ->
                    httpClient
                        .post("/deltaker/${deltaker.id}/endre-innhold-kodeverk") {
                            createPostRequest(request)
                        }.apply {
                            status shouldBe HttpStatusCode.BadRequest
                        }
                }
            }

            @Test
            fun `oppdater opplæringskategorisering - beskrivelse over maks lengde - sanitiseres til 250`() {
                val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN))
                val expected = setupMocksLocal(
                    deltaker = deltaker,
                    oppdatert = deltaker,
                    gjennomforingType = GjennomforingType.Enkeltplass,
                )

                val request = endreOpplaringKategoriseringRequest.copy(beskrivelse = "a".repeat(251))

                withTestApplicationContext { httpClient ->
                    httpClient
                        .post("/deltaker/${deltaker.id}/endre-innhold-kodeverk") {
                            createPostRequest(request)
                        }.apply {
                            status shouldBe HttpStatusCode.OK
                        }
                }
            }
        }

        @Nested
        inner class EndrePrisinfoTests {
            @Test
            fun `oppdater prisinfo - har tilgang - returnerer deltaker`() {
                val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
                val expected = setupMocksLocal(deltaker, deltaker)

                withTestApplicationContext { httpClient ->
                    httpClient
                        .post("/deltaker/${deltaker.id}/endre-prisinfo") {
                            createPostRequest(endrePrisinfoRequest)
                        }.apply {
                            status shouldBe HttpStatusCode.OK
                            bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                        }
                }
            }

            @Test
            fun `oppdater prisinfo - ugyldig prisinformasjon - returnerer 400`() {
                val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
                val ugyldigRequest = EndrePrisinfoRequest(
                    prisinformasjon = PrisinformasjonDto.Anskaffelse(pris = 0),
                    begrunnelse = "begrunnelse",
                )
                setupMocks(deltaker)
                coEvery { amtDeltakerClient.getDeltaker(deltaker.id) } returns lagDeltakerResponse(deltaker)

                withTestApplicationContext { httpClient ->
                    httpClient
                        .post("/deltaker/${deltaker.id}/endre-prisinfo") {
                            createPostRequest(ugyldigRequest)
                        }.apply {
                            status shouldBe HttpStatusCode.BadRequest
                            // Response should not be a valid DeltakerResponse
                            val body = bodyAsText()
                            body.contains("Prisinformasjon er ikke gyldig: Pris må være større enn 0") shouldBe true
                        }
                }
            }
        }

        // ---- startdato ----

        @Test
        fun `oppdater startdato - har tilgang - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            val oppdatert = deltaker.copy(startdato = startdatoRequest.startdato, sluttdato = startdatoRequest.sluttdato)
            val expected = setupMocksLocal(deltaker, oppdatert)

            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/startdato") { createPostRequest(startdatoRequest) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }

        // ---- sluttdato ----

        @Test
        fun `endre sluttdato - har tilgang - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                sluttdato = LocalDate.now().minusDays(3),
            )
            val oppdatert = deltaker.copy(sluttdato = sluttdatoRequest.sluttdato)
            val expected = setupMocksLocal(deltaker, oppdatert)

            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/sluttdato") { createPostRequest(sluttdatoRequest) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }

        // ---- sluttårsak ----

        @Test
        fun `endre sluttarsak - har tilgang - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET))
            val oppdatert = deltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET, sluttarsakRequest.aarsak.toDeltakerStatusAarsak()),
            )
            val expected = setupMocksLocal(deltaker, oppdatert)

            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/sluttarsak") { createPostRequest(sluttarsakRequest) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }

        // ---- ikke aktuell ----

        @Test
        fun `ikke aktuell - har tilgang - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            val oppdatert = deltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL, ikkeAktuellRequest.aarsak.toDeltakerStatusAarsak()),
            )
            val expected = setupMocksLocal(deltaker, oppdatert)

            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/ikke-aktuell") { createPostRequest(ikkeAktuellRequest) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }

        // ---- reaktiver ----

        @Test
        fun `reaktiver - har tilgang - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL))
            val oppdatert = deltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
            )
            val expected = setupMocksLocal(deltaker, oppdatert)

            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/reaktiver") { createPostRequest(reaktiverDeltakelseRequest) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }

        // ---- forleng ----

        @Test
        fun `forleng - har tilgang - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = forlengDeltakelseRequest.sluttdato.minusDays(3),
            )
            val oppdatert = deltaker.copy(sluttdato = forlengDeltakelseRequest.sluttdato)
            val expected = setupMocksLocal(deltaker, oppdatert)

            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/forleng") { createPostRequest(forlengDeltakelseRequest) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }

        @Test
        fun `forleng - ny dato tidligere enn forrige - returnerer 400`() {
            val deltaker = lagDeltakerOld(sluttdato = forlengDeltakelseRequest.sluttdato.plusDays(5))
            setupMocksLocal(deltaker, null)

            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/forleng") { createPostRequest(forlengDeltakelseRequest) }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                }
            }
        }

        // ---- avslutt ----

        @Test
        fun `avslutt - har deltatt - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val oppdatert = deltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET, avsluttDeltakelseRequest.aarsak!!.toDeltakerStatusAarsak()),
                sluttdato = avsluttDeltakelseRequest.sluttdato,
            )
            val expected = setupMocksLocal(deltaker, oppdatert)

            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttDeltakelseRequest) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }

        @Test
        fun `avslutt - har ikke deltatt - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val oppdatert = deltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL, ikkeAktuellRequest.aarsak.toDeltakerStatusAarsak()),
                startdato = null,
                sluttdato = null,
            )
            val avsluttIkkeDeltatt = AvsluttDeltakelseRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT),
                sluttdato = null,
                harDeltatt = false,
                begrunnelse = "begrunnelse",
                forslagId = null,
            )
            val expected = setupMocksLocal(deltaker, oppdatert)

            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/avslutt") { createPostRequest(avsluttIkkeDeltatt) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }

        // ---- endre-avslutning ----

        @Test
        fun `endre-avslutning - har tilgang - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.FULLFORT))
            val oppdatert = deltaker.copy(
                status = lagDeltakerStatus(
                    DeltakerStatus.Type.AVBRUTT,
                    DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB).toDeltakerStatusAarsak(),
                ),
            )
            val request = EndreAvslutningRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB),
                harDeltatt = null,
                harFullfort = false,
                begrunnelse = "begrunnelse",
                sluttdato = deltaker.sluttdato,
                forslagId = null,
            )
            val expected = setupMocksLocal(deltaker, oppdatert)
            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/endre-avslutning") { createPostRequest(request) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }

        // ---- fjern oppstartsdato ----

        @Test
        fun `fjern oppstartsdato - har tilgang - returnerer oppdatert deltaker`() {
            val deltaker = lagDeltakerOld(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().plusWeeks(1),
                sluttdato = LocalDate.now().plusMonths(3),
            )
            val oppdatert = deltaker.copy(startdato = null, sluttdato = null)
            val expected = setupMocksLocal(deltaker, oppdatert)

            withTestApplicationContext { httpClient ->
                httpClient.post("/deltaker/${deltaker.id}/fjern-oppstartsdato") { createPostRequest(fjernOppstartsdatoRequest) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }

        // ---- avvis forslag ----

        @Test
        fun `avvis forslag - har tilgang - returnerer deltaker`() {
            val deltaker = lagDeltakerOld()
            val forslag = lagForslag(deltakerId = deltaker.id)

            coEvery { amtDeltakerClient.getPersonidentForForslag(forslag.id) } returns
                PersonIdentResponse(deltaker.navBruker.personident).personident

            val expected = setupMocksLocal(deltaker, deltaker)

            every { forslagRepository.delete(forslag.id) } just Runs

            withTestApplicationContext { httpClient ->
                httpClient.post("/forslag/${forslag.id}/avvis") { createPostRequest(avvisForslagRequest) }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
            }
        }
    }
// ---- Hjelpefunksjoner ----

    private val deltakerRequest = DeltakerRequest("1234")
    private val bakgrunnsinformasjonRequest = EndreBakgrunnsinformasjonRequest("Oppdatert bakgrunnsinformasjon")
    private val innholdRequest = EndreInnholdRequest(emptyList())

    private val endrePrisinfoRequest = EndrePrisinfoRequest(
        prisinformasjon = PrisinformasjonDto.IngenKostnader(
            aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
            tilleggsopplysninger = "Ingen kostnader",
        ),
        begrunnelse = "begrunnelse",
    )

    private val endreOpplaringKategoriseringRequest = EndreOpplaringKategoriseringRequest(
        opplaringKategoriseringValg = setOf(
            OpplaringKategoriseringValgRequest(
                representerer = OpplaringKategoriseringType.BRANSJE_ID,
                valgteIder = setOf(UUID.randomUUID()),
            ),
        ),
        sertifiseringValg = setOf(SertifiseringValg(id = 1, navn = "Truckførerbevis")),
        beskrivelse = "begrunnelse",
        pavirkerPris = false,
    )

    private val deltakelsesmengdeRequest = EndreDeltakelsesmengdeRequest(
        deltakelsesprosent = 50,
        dagerPerUke = 3,
        begrunnelse = "begrunnelse",
        gyldigFra = LocalDate.now(),
        pavirkerPris = false,
        forslagId = null,
    )
    private val startdatoRequest = EndreStartdatoRequest(
        LocalDate.now().plusWeeks(1),
        sluttdato = LocalDate.now().plusMonths(2),
        "begrunnelse",
        null,
        null,
    )
    private val sluttdatoRequest = EndreSluttdatoRequest(LocalDate.now().minusDays(1), "begrunnelse", null)
    private val sluttarsakRequest = EndreSluttarsakRequest(
        DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT),
        "begrunnelse",
        null,
    )
    private val ikkeAktuellRequest = IkkeAktuellRequest(
        DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB),
        "begrunnelse",
        null,
    )
    private val reaktiverDeltakelseRequest = ReaktiverDeltakelseRequest("begrunnelse")
    private val forlengDeltakelseRequest = ForlengDeltakelseRequest(
        sluttdato = LocalDate.now().plusWeeks(3),
        begrunnelse = "begrunnelse",
        pavirkerPris = null,
        forslagId = null,
    )
    private val avsluttDeltakelseRequest = AvsluttDeltakelseRequest(
        aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB),
        sluttdato = LocalDate.now(),
        harDeltatt = true,
        harFullfort = null,
        begrunnelse = "begrunnelse",
        forslagId = null,
    )
    private val endreAvslutningRequest = EndreAvslutningRequest(
        aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB),
        harDeltatt = true,
        harFullfort = null,
        begrunnelse = "begrunnelse",
        sluttdato = null,
        forslagId = null,
    )
    private val fjernOppstartsdatoRequest = FjernOppstartsdatoRequest("begrunnelse", null)
    private val avvisForslagRequest = AvvisForslagRequest(
        begrunnelse = "Avvist fordi..",
    )

    private fun setupMocks(deltaker: Deltaker) {
        every { sporbarhetsloggService.sendAuditLog(any(), any()) } just Runs
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        every { commonUnleashToggle.erKometMasterForTiltakstype(any<String>()) } returns true
        every { commonUnleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(deltaker.id) } returns
            PersonIdentResponse(deltaker.navBruker.personident).personident
    }
}
