package no.nav.amt.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagForslag
import no.nav.amt.internapi.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.AvvisForslagRequest
import no.nav.amt.internapi.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.internapi.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.internapi.deltaker.request.EndreAvslutningRequest
import no.nav.amt.internapi.deltaker.request.EndretInnholdRequest
import no.nav.amt.internapi.deltaker.request.EndringRequest
import no.nav.amt.internapi.deltaker.request.FjernOppstartsdatoRequest
import no.nav.amt.internapi.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.IkkeAktuellRequest
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.internapi.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.SluttarsakRequest
import no.nav.amt.internapi.deltaker.request.SluttdatoRequest
import no.nav.amt.internapi.deltaker.request.StartdatoRequest
import no.nav.amt.internapi.deltaker.request.toInnholdModel
import no.nav.amt.internapi.deltaker.response.DeltakerHistorikkDataResponse
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.TestData.randomEnhetsnummer
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class VeilederApiTest : IntegrationTestBase() {
    override val deltakerService: DeltakerService = mockk()
    override val forslagService: ForslagService = mockk()
    override val deltakerHistorikkService = mockk<DeltakerHistorikkService>()
    override val deltakerResponseBuilder = mockk<DeltakerResponseBuilder>()

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.post("/deltaker/${UUID.randomUUID()}/endre-deltaker") { setBody("foo") }.status shouldBe
                HttpStatusCode.Unauthorized
            client.post("/deltaker/${UUID.randomUUID()}/sist-besokt") { setBody("foo") }.status shouldBe
                HttpStatusCode.Unauthorized
            client.get("/deltaker/${UUID.randomUUID()}/historikk").status shouldBe
                HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post sist-besokt - har tilgang - returnerer 200`() {
        // Arrange
        val deltakerInTest = lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )

        val sistBesoktInTest = ZonedDateTime.now()

        every {
            deltakerService.oppdaterSistBesokt(
                deltakerId = deltakerInTest.id,
                sistBesokt = any(),
            )
        } just Runs

        // Act
        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${deltakerInTest.id}/sist-besokt") {
                postRequest(sistBesoktInTest)
            }

            response.status shouldBe HttpStatusCode.OK
        }

        verify {
            deltakerService.oppdaterSistBesokt(
                deltakerId = deltakerInTest.id,
                sistBesokt = sistBesoktInTest.withZoneSameInstant(ZoneOffset.UTC),
            )
        }
    }

    @Test
    fun `avvis forslag - har tilgang - returnerer 200`() {
        val forslagId = UUID.randomUUID()
        val avvisForslagRequest = AvvisForslagRequest(
            begrunnelse = "begrunnelse",
            avvistAvAnsatt = UUID.randomUUID(),
            avvistAvEnhet = "Enhet",
        )
        val deltaker = lagDeltaker()

        val deltakerResponse = TestData.lagDeltakerResponse(deltaker)
        coEvery { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        coEvery { deltakerResponseBuilder.buildDeltakerResponse(deltaker) } returns deltakerResponse
        every { forslagRepository.get(forslagId) } returns Result.success(lagForslag(id = forslagId, deltakerId = deltaker.id))

        coEvery {
            forslagService.avvisForslag(
                forslagId,
                avvisForslagRequest.begrunnelse,
                avvisForslagRequest.avvistAvAnsatt,
                avvisForslagRequest.avvistAvEnhet,
            )
        } just Runs

        withTestApplicationContext { client ->
            val response = client.post("/avvis-forslag/$forslagId") {
                postRequest(avvisForslagRequest)
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe objectMapper.writeValueAsString(deltakerResponse)
        }
    }

    @Nested
    inner class EndreDeltakerTest {
        @Test
        fun `post bakgrunnsinformasjon til felles endepunkt for endringer - har tilgang - returnerer 200`() {
            val bakgrunnsinformasjonRequest = BakgrunnsinformasjonRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                bakgrunnsinformasjon = "bakgrunnsinformasjon",
            )

            val historikk =
                listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = bakgrunnsinformasjonRequest.toEndring())))
            val deltaker = lagDeltaker(bakgrunnsinformasjon = bakgrunnsinformasjonRequest.bakgrunnsinformasjon)

            runEndringTest(bakgrunnsinformasjonRequest, deltaker, historikk)
        }

        @Test
        fun `post innhold - har tilgang - returnerer 200`() {
            val innholdRequest = EndretInnholdRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                innholdselementer = listOf(
                    InnholdsElementRequest(
                        innholdskode = "kode",
                        beskrivelse = "beskrivelse",
                    ),
                ),
            )

            val deltaker = lagDeltaker(
                innhold = Deltakelsesinnhold(
                    ledetekst = "test",
                    innhold = innholdRequest.innholdselementer.toInnholdModel(TestData.lagTiltakstype()),
                ),
            )
            val innholdEndring = innholdRequest.toEndring(deltaker.deltakerliste.tiltakstype)
            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = innholdEndring)))

            runEndringTest(innholdRequest, deltaker, historikk)
        }

        @Test
        fun `post deltakelsesmengde - har tilgang - returnerer 200`() {
            val deltakelsesmengdeRequest = DeltakelsesmengdeRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                forslagId = null,
                deltakelsesprosent = 50,
                dagerPerUke = 2,
                begrunnelse = "begrunnelse",
                gyldigFra = LocalDate.now(),
            )

            val deltakelsesmengdeEndring = deltakelsesmengdeRequest.toEndring()
            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = deltakelsesmengdeEndring)))
            val deltaker = lagDeltaker(
                deltakelsesprosent = deltakelsesmengdeEndring.deltakelsesprosent,
                dagerPerUke = deltakelsesmengdeEndring.dagerPerUke,
            )

            runEndringTest(deltakelsesmengdeRequest, deltaker, historikk)
        }

        @Test
        fun `post startdato - har tilgang - returnerer 200`() {
            val startdatoRequest = StartdatoRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                forslagId = null,
                startdato = LocalDate.now().minusDays(2),
                sluttdato = LocalDate.now().plusMonths(2),
                begrunnelse = "begrunnelse",
            )

            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = startdatoRequest.toEndring())))
            val deltaker = lagDeltaker(startdato = startdatoRequest.startdato)

            runEndringTest(startdatoRequest, deltaker, historikk)
        }

        @Test
        fun `post sluttdato - har tilgang - returnerer 200`() {
            val sluttdatoRequest = SluttdatoRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                forslagId = null,
                sluttdato = LocalDate.now().minusDays(2),
                begrunnelse = "begrunnelse",
            )

            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = sluttdatoRequest.toEndring())))
            val deltaker = lagDeltaker(sluttdato = sluttdatoRequest.sluttdato)

            runEndringTest(sluttdatoRequest, deltaker, historikk)
        }

        @Test
        fun `post sluttarsak - har tilgang - returnerer 200`() {
            val sluttarsakRequest = SluttarsakRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                forslagId = null,
                aarsak = DeltakerEndring.Aarsak(
                    type = DeltakerEndring.Aarsak.Type.ANNET,
                    beskrivelse = "beskrivelse",
                ),
                begrunnelse = "begrunnelse",
            )

            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = sluttarsakRequest.toEndring())))
            val deltaker = lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                ),
            )

            runEndringTest(sluttarsakRequest, deltaker, historikk)
        }

        @Test
        fun `post forleng - har tilgang - returnerer 200`() {
            val forlengDeltakelseRequest = ForlengDeltakelseRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                forslagId = null,
                sluttdato = LocalDate.now().plusWeeks(2),
                begrunnelse = "begrunnelse",
            )

            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = forlengDeltakelseRequest.toEndring())))
            val deltaker = lagDeltaker(sluttdato = forlengDeltakelseRequest.sluttdato)

            runEndringTest(forlengDeltakelseRequest, deltaker, historikk)
        }

        @Test
        fun `post ikke aktuell - har tilgang - returnerer 200`() {
            val ikkeAktuellRequest = IkkeAktuellRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                forslagId = null,
                aarsak = DeltakerEndring.Aarsak(
                    type = DeltakerEndring.Aarsak.Type.IKKE_MOTT,
                    beskrivelse = null,
                ),
                begrunnelse = "begrunnelse",
            )

            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = ikkeAktuellRequest.toEndring())))
            val deltaker = lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                    aarsakType = DeltakerStatus.Aarsak.Type.IKKE_MOTT,
                ),
            )

            runEndringTest(ikkeAktuellRequest, deltaker, historikk)
        }

        @Test
        fun `post avslutt deltakelse - har tilgang - returnerer 200`() {
            val avsluttDeltakelseRequest = AvsluttDeltakelseRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                forslagId = null,
                sluttdato = LocalDate.now(),
                aarsak = DeltakerEndring.Aarsak(
                    type = DeltakerEndring.Aarsak.Type.FATT_JOBB,
                    beskrivelse = null,
                ),
                begrunnelse = "begrunnelse",
                harFullfort = null,
            )

            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = avsluttDeltakelseRequest.toEndring())))
            val deltaker = lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                ),
                sluttdato = avsluttDeltakelseRequest.sluttdato,
            )

            runEndringTest(avsluttDeltakelseRequest, deltaker, historikk)
        }

        @Test
        fun `post endre avslutning - har tilgang - returnerer 200`() {
            val endreAvslutningRequest = EndreAvslutningRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                forslagId = null,
                aarsak = DeltakerEndring.Aarsak(
                    type = DeltakerEndring.Aarsak.Type.UTDANNING,
                    beskrivelse = null,
                ),
                begrunnelse = "begrunnelse",
                sluttdato = LocalDate.now(),
                harFullfort = true,
            )

            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endreAvslutningRequest.toEndring())))
            val deltaker = lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.FULLFORT,
                    aarsakType = null,
                ),
            )

            runEndringTest(endreAvslutningRequest, deltaker, historikk)
        }

        @Test
        fun `post reaktiver - har tilgang - returnerer 200`() {
            val reaktiverDeltakelseRequest = ReaktiverDeltakelseRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                begrunnelse = "begrunnelse",
            )

            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = reaktiverDeltakelseRequest.toEndring())))
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
            )

            runEndringTest(reaktiverDeltakelseRequest, deltaker, historikk)
        }

        @Test
        fun `post fjern oppstartsdato - har tilgang - returnerer 200`() {
            val fjernOppstartsdatoRequest = FjernOppstartsdatoRequest(
                endretAv = randomIdent(),
                endretAvEnhet = randomEnhetsnummer(),
                forslagId = null,
                begrunnelse = "begrunnelse",
            )

            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = fjernOppstartsdatoRequest.toEndring())))
            val deltaker = lagDeltaker(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
            )

            runEndringTest(fjernOppstartsdatoRequest, deltaker, historikk)
        }
    }

    @Nested
    inner class GetHistorikkTest {
        @Test
        fun `get historikk - har tilgang - returnerer 200`() {
            val arrangor = lagArrangor()
            val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)
            val deltaker = lagDeltaker(deltakerliste = deltakerliste)
            val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(deltakerId = deltaker.id)))
            val navAnsatte = historikk.flatMap { it.navAnsatte() }.map { lagNavAnsatt(id = it) }
            val navEnheter = historikk.flatMap { it.navEnheter() }.map { lagNavEnhet(id = it) }

            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
            every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk
            every { navAnsattRepository.getManyById(any()) } returns navAnsatte
            every { navEnhetRepository.getMany(any()) } returns navEnheter
            every { arrangorRepository.get(arrangor.id) } returns arrangor

            withTestApplicationContext { client ->
                val response = client.get("/deltaker/${deltaker.id}/historikk") {
                    noBodyRequest()
                }

                response.status shouldBe HttpStatusCode.OK
                val body = objectMapper.readValue(response.bodyAsText(), DeltakerHistorikkDataResponse::class.java)
                body.historikk shouldBe historikk
                body.arrangornavn shouldBe arrangor.navn
                body.oppstartstype shouldBe deltakerliste.oppstart
                body.ansatte shouldBe navAnsatte.associateBy { it.id }
                body.enheter shouldBe navEnheter.associateBy { it.id }
            }
        }

        @Test
        fun `get historikk - deltakerliste med TRENGER_GODKJENNING - returnerer pameldingstype TRENGER_GODKJENNING`() {
            val arrangor = lagArrangor()
            val deltakerliste = TestData.lagDeltakerliste(
                arrangor = arrangor,
                pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
            )
            val vedtak = TestData.lagVedtak()
            val deltaker = lagDeltaker(deltakerliste = deltakerliste)
            val historikk = listOf(
                DeltakerHistorikk.Vedtak(vedtak),
                DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(deltakerId = deltaker.id)),
            )
            val navAnsatte = historikk.flatMap { it.navAnsatte() }.map { lagNavAnsatt(id = it) }
            val navEnheter = historikk.flatMap { it.navEnheter() }.map { lagNavEnhet(id = it) }

            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
            every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk
            every { navAnsattRepository.getManyById(any()) } returns navAnsatte
            every { navEnhetRepository.getMany(any()) } returns navEnheter
            every { arrangorRepository.get(arrangor.id) } returns arrangor

            withTestApplicationContext { client ->
                val response = client.get("/deltaker/${deltaker.id}/historikk") { noBodyRequest() }

                response.status shouldBe HttpStatusCode.OK
                val body = objectMapper.readValue(response.bodyAsText(), DeltakerHistorikkDataResponse::class.java)
                body.pameldingstype shouldBe GjennomforingPameldingType.TRENGER_GODKJENNING
                body.historikk.size shouldBe 2
                body.historikk.any { it is DeltakerHistorikk.Vedtak } shouldBe true
            }
        }

        @Test
        fun `get historikk - deltakerliste med DIREKTE_VEDTAK - returnerer pameldingstype DIREKTE_VEDTAK`() {
            val arrangor = lagArrangor()
            val deltakerliste = TestData.lagDeltakerliste(
                arrangor = arrangor,
                pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
            )
            val vedtak = TestData.lagVedtak()
            val deltaker = lagDeltaker(deltakerliste = deltakerliste)
            val historikk = listOf(
                DeltakerHistorikk.Vedtak(vedtak),
                DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(deltakerId = deltaker.id)),
            )
            val navAnsatte = historikk.flatMap { it.navAnsatte() }.map { lagNavAnsatt(id = it) }
            val navEnheter = historikk.flatMap { it.navEnheter() }.map { lagNavEnhet(id = it) }

            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
            every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk
            every { navAnsattRepository.getManyById(any()) } returns navAnsatte
            every { navEnhetRepository.getMany(any()) } returns navEnheter
            every { arrangorRepository.get(arrangor.id) } returns arrangor

            withTestApplicationContext { client ->
                val response = client.get("/deltaker/${deltaker.id}/historikk") { noBodyRequest() }

                response.status shouldBe HttpStatusCode.OK
                val body = objectMapper.readValue(response.bodyAsText(), DeltakerHistorikkDataResponse::class.java)
                body.pameldingstype shouldBe GjennomforingPameldingType.DIREKTE_VEDTAK
                body.historikk.size shouldBe 2
            }
        }
    }

    private fun runEndringTest(
        request: EndringRequest,
        deltaker: Deltaker,
        historikk: List<DeltakerHistorikk.Endring>,
    ) {
        val deltakerResponse = TestData.lagDeltakerResponse(deltaker)
        coEvery { deltakerService.upsertEndretDeltaker(deltaker.id, request) } returns deltaker
        coEvery { deltakerResponseBuilder.buildDeltakerResponse(deltaker) } returns deltakerResponse
        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${deltaker.id}/endre-deltaker") {
                postRequest(request)
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe objectMapper.writeValueAsString(deltakerResponse)
        }

        coVerify { deltakerService.upsertEndretDeltaker(deltaker.id, request) }
    }
}
