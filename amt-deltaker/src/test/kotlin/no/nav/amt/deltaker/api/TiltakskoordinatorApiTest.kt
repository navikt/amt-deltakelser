package no.nav.amt.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.api.response.TiltakskoordinatorResponseBuilder
import no.nav.amt.deltaker.api.tiltaksansvarlig.DeltakerOppdateringResult
import no.nav.amt.deltaker.api.tiltaksansvarlig.ResponseMapper.toDeltakerOppdatering
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksansvarlig.TiltaksansvarligService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.internapi.deltaker.request.PageRequest
import no.nav.amt.internapi.deltaker.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.PaginatedResult
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorDeltakerResponse
import no.nav.amt.internapi.tiltakskoordinator.request.DeltakereRequest
import no.nav.amt.internapi.tiltakskoordinator.request.GiAvslagRequest
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class TiltakskoordinatorApiTest : IntegrationTestBase() {
    override val tiltaksansvarligService = mockk<TiltaksansvarligService>()
    override val deltakerHistorikkService = mockk<DeltakerHistorikkService>()
    override val deltakerRepository = mockk<DeltakerRepository>()
    override val deltakerResponseBuilder = mockk<DeltakerResponseBuilder>()
    override val tiltakskoordinatorResponseBuilder = mockk<TiltakskoordinatorResponseBuilder>()
    override val arrangorService: ArrangorService = mockk()
    override val deltakerlisteRepository: DeltakerlisteRepository = mockk()

    @Nested
    inner class GetGjennomforingTests {
        @Test
        fun `get gjennomforing - mangler token - returnerer 401`() {
            withTestApplicationContext { client ->
                client.get("/gjennomforing/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `get gjennomforing - gjennomforing finnes - returnerer 200 med gjennomforing`() {
            val deltakerliste = lagDeltakerliste()

            val expectedResponse = GjennomforingResponse(
                id = deltakerliste.id,
                type = deltakerliste.gjennomforingstype,
                tiltakstype = deltakerliste.tiltakstype,
                navn = deltakerliste.navn,
                status = deltakerliste.status,
                startDato = deltakerliste.startDato,
                sluttDato = deltakerliste.sluttDato,
                antallPlasser = deltakerliste.antallPlasser,
                oppstart = deltakerliste.oppstart,
                apentForPamelding = deltakerliste.apentForPamelding,
                oppmoteSted = deltakerliste.oppmoteSted,
                arrangor = null,
                pameldingstype = deltakerliste.pameldingstype,
            )

            every { deltakerlisteRepository.get(deltakerliste.id) } returns Result.success(deltakerliste)
            every {
                deltakerResponseBuilder.buildGjennomforingResponse(
                    deltakerliste = deltakerliste,
                    includeKodeverk = false,
                )
            } returns expectedResponse

            withTestApplicationContext { client ->
                client.get("/gjennomforing/${deltakerliste.id}") { noBodyRequest() }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expectedResponse)
                }
            }
        }

        @Test
        fun `get gjennomforing - gjennomforing finnes ikke - returnerer 404`() {
            every {
                deltakerlisteRepository.get(any())
            } returns Result.failure(NoSuchElementException("Fant ikke deltakerliste"))

            withTestApplicationContext { client ->
                client.get("/gjennomforing/${UUID.randomUUID()}") { noBodyRequest() }.apply {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.post("$API_PATH/${UUID.randomUUID()}") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/del-med-arrangor") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/sett-paa-venteliste") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/tildel-plass") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/gi-avslag") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `getDeltakereForGjennomforing - har tilgang - returnerer 200 og deltakere fra tiltakskoordinatorResponseBuilder`() {
        val gjennomforingId = UUID.randomUUID()
        val request = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = gjennomforingId,
            pageRequest = PageRequest(
                sort = TiltaksKoordinatorDeltakerlisteRequest.SortColumn.NAVN,
                page = 2,
                pageSize = 50,
            ),
        )
        val deltakerResponse = mockk<TiltakskoordinatorDeltakerResponse>(relaxed = true)
        val expectedResponse = PaginatedResult(
            totalCount = 1,
            pageSize = 50,
            data = listOf(deltakerResponse),
        )

        coEvery { tiltakskoordinatorResponseBuilder.buildResponse(request) } returns expectedResponse

        withTestApplicationContext { client ->
            client
                .post("$API_PATH/$gjennomforingId") {
                    postRequest(request)
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expectedResponse)
                }
        }

        coVerify(exactly = 1) { tiltakskoordinatorResponseBuilder.buildResponse(request) }
    }

    @Test
    fun `getDeltakereForGjennomforing - gjennomforingId i path og body matcher ikke - returnerer 400`() {
        val pathGjennomforingId = UUID.randomUUID()
        val request = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = UUID.randomUUID(),
        )

        withTestApplicationContext { client ->
            client
                .post("$API_PATH/$pathGjennomforingId") {
                    postRequest(request)
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                }
        }

        coVerify(exactly = 0) { tiltakskoordinatorResponseBuilder.buildResponse(any()) }
    }

    @Test
    fun `gi-avslag - har tilgang - returnerer 200 og mappet deltakeroppdatering`() {
        val request = GiAvslagRequest(
            deltakerId = deltaker.id,
            avslag = EndringFraTiltakskoordinator.Avslag(
                aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(
                    type = EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT,
                    beskrivelse = null,
                ),
                begrunnelse = null,
            ),
            endretAv = "Nav Veiledersen",
        )

        coEvery {
            tiltaksansvarligService.giAvslag(request.deltakerId, request.avslag, request.endretAv)
        } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk

        withTestApplicationContext { client ->
            client.post("$API_PATH/gi-avslag") { postRequest(request) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(
                    deltaker.toDeltakerOppdatering(historikk),
                )
            }
        }
    }

    @Test
    fun `del-med-arrangor - har tilgang - returnerer 200`() {
        coEvery { tiltaksansvarligService.oppdaterDeltakere(any(), any(), any()) } returns listOf(deltaker.toDeltakerOppdateringResult())
        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns emptyList()

        withTestApplicationContext { client ->
            client.post("$API_PATH/del-med-arrangor") { postRequest(delMedArrangorRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(listOf(deltaker.toDeltakerResponse(emptyList())))
            }
        }
    }

    @Test
    fun `sett-paa-venteliste - har tilgang - returnerer 200`() {
        coEvery { tiltaksansvarligService.oppdaterDeltakere(any(), any(), any()) } returns listOf(deltaker.toDeltakerOppdateringResult())
        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk

        val request = DeltakereRequest(
            deltakere = listOf(deltaker.id),
            endretAv = "Nav Veiledersen",
        )

        withTestApplicationContext { client ->
            client.post("$API_PATH/sett-paa-venteliste") { postRequest(request) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(listOf(deltaker.toDeltakerResponse(historikk)))
            }
        }
    }

    @Test
    fun `tildel plass - har tilgang - returnerer 200`() {
        coEvery { tiltaksansvarligService.oppdaterDeltakere(any(), any(), any()) } returns listOf(deltaker.toDeltakerOppdateringResult())
        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk

        val request = DeltakereRequest(
            deltakere = listOf(deltaker.id),
            endretAv = "Nav Veiledersen",
        )

        withTestApplicationContext { client ->
            client.post("$API_PATH/tildel-plass") { postRequest(request) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(listOf(deltaker.toDeltakerResponse(historikk)))
            }
        }
    }

    companion object {
        private const val API_PATH = "/tiltakskoordinator/deltakere"
        private val deltaker = TestData.lagDeltaker()
        private val historikk = emptyList<DeltakerHistorikk>()

        private val delMedArrangorRequest = DelMedArrangorRequest(
            endretAv = "koordinator",
            deltakerIder = listOf(UUID.randomUUID()),
        )

        private fun Deltaker.toDeltakerOppdateringResult() = DeltakerOppdateringResult(
            deltaker = this,
            isSuccess = true,
            exception = null,
        )

        private fun Deltaker.toDeltakerResponse(
            historikk: List<DeltakerHistorikk>,
            feilkode: DeltakerOppdateringFeilkode? = null,
        ): DeltakerOppdateringResponse = DeltakerOppdateringResponse(
            id = id,
            startdato = startdato,
            sluttdato = sluttdato,
            dagerPerUke = dagerPerUke,
            deltakelsesprosent = deltakelsesprosent,
            bakgrunnsinformasjon = bakgrunnsinformasjon,
            deltakelsesinnhold = deltakelsesinnhold,
            status = status,
            historikk = historikk,
            sistEndret = sistEndret,
            erManueltDeltMedArrangor = erManueltDeltMedArrangor,
            feilkode = feilkode,
        )
    }
}
