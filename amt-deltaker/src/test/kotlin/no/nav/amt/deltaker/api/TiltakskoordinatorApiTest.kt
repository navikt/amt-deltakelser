package no.nav.amt.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.api.response.ResponseBuilder
import no.nav.amt.deltaker.api.tiltaksansvarlig.DeltakerOppdateringResult
import no.nav.amt.deltaker.api.tiltaksansvarlig.ResponseMapper.toDeltakerOppdatering
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksansvarlig.TiltaksansvarligService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.DeltakereResponse
import no.nav.amt.internapi.tiltakskoordinator.request.DeltakereRequest
import no.nav.amt.internapi.tiltakskoordinator.request.GiAvslagRequest
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.util.UUID

class TiltakskoordinatorApiTest : IntegrationTestBase() {
    override val tiltaksansvarligService = mockk<TiltaksansvarligService>()
    override val deltakerHistorikkService = mockk<DeltakerHistorikkService>()
    override val deltakerRepository = mockk<DeltakerRepository>()
    override val responseBuilder = mockk<ResponseBuilder>()

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.get("$API_PATH/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/del-med-arrangor") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/sett-paa-venteliste") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/tildel-plass") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/gi-avslag") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `getDeltakereForGjennomforing - har tilgang - returnerer 200 og deltakere fra responseBuilder`() {
        val gjennomforingId = UUID.randomUUID()
        val deltakere = listOf(deltaker)
        val deltakerResponse = mockk<DeltakerResponse>(relaxed = true)
        val expectedResponse = DeltakereResponse(listOf(deltakerResponse))

        every { deltakerRepository.getForGjennomforing(gjennomforingId) } returns deltakere
        coEvery {
            responseBuilder.buildDeltakerResponse(
                deltaker = deltaker,
                includeKodeverk = any(),
            )
        } returns deltakerResponse

        withTestApplicationContext { client ->
            client
                .get("$API_PATH/$gjennomforingId") {
                    noBodyRequest()
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(expectedResponse)
                }
        }
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
