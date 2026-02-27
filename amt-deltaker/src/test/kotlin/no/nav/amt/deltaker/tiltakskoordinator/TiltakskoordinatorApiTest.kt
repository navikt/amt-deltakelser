package no.nav.amt.deltaker.tiltakskoordinator

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.deltaker.DeltakerOppdateringResult
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.request.DeltakereRequest
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.Test
import java.util.UUID

class TiltakskoordinatorApiTest : RouteTestBase() {
    override val unleashToggle = mockk<CommonUnleashToggle>()

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.post("$API_PATH/del-med-arrangor") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/sett-paa-venteliste") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/tildel-plass") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("$API_PATH/gi-avslag") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `del-med-arrangor - har tilgang - returnerer 200`() {
        coEvery { deltakerService.oppdaterDeltakere(any(), any(), any()) } returns listOf(deltaker.toDeltakerOppdateringResult())
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
        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
        coEvery { deltakerService.oppdaterDeltakere(any(), any(), any()) } returns listOf(deltaker.toDeltakerOppdateringResult())
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
        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
        coEvery { deltakerService.oppdaterDeltakere(any(), any(), any()) } returns listOf(deltaker.toDeltakerOppdateringResult())
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
        private val deltaker = lagDeltaker()
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
