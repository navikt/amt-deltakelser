package no.nav.amt.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import no.nav.amt.deltaker.deltaker.api.DtoMappers
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndreAvslutningRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.FjernOppstartsdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.IkkeAktuellRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.InnholdRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttarsakRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.StartdatoRequest
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class VeilederApiTest : RouteTestBase() {
    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.post("/deltaker/${UUID.randomUUID()}/endre-deltaker") { setBody("foo") }.status shouldBe
                HttpStatusCode.Companion.Unauthorized
            client.post("/deltaker/${UUID.randomUUID()}/sist-besokt") { setBody("foo") }.status shouldBe
                HttpStatusCode.Companion.Unauthorized
        }
    }

    @Test
    fun `post bakgrunnsinformasjon til felles endepunkt for endringer - har tilgang - returnerer 200`() {
        val bakgrunnsinformasjonRequest = BakgrunnsinformasjonRequest(
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
            bakgrunnsinformasjon = "bakgrunnsinformasjon",
        )

        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = bakgrunnsinformasjonRequest.toEndring())))
        val deltaker = TestData.lagDeltaker(bakgrunnsinformasjon = bakgrunnsinformasjonRequest.bakgrunnsinformasjon)

        runEndringTest(bakgrunnsinformasjonRequest, deltaker, historikk)
    }

    @Test
    fun `post innhold - har tilgang - returnerer 200`() {
        val innholdRequest = InnholdRequest(
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
            deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = "ledetekst",
                innhold = listOf(
                    Innhold(
                        tekst = "tekst",
                        innholdskode = "kode",
                        valgt = true,
                        beskrivelse = "beskrivelse",
                    ),
                ),
            ),
        )

        val innholdEndring = innholdRequest.toEndring()
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = innholdEndring)))
        val deltaker = TestData.lagDeltaker(
            innhold = Deltakelsesinnhold(
                ledetekst = "test",
                innhold = innholdEndring.innhold,
            ),
        )

        runEndringTest(innholdRequest, deltaker, historikk)
    }

    @Test
    fun `post deltakelsesmengde - har tilgang - returnerer 200`() {
        val deltakelsesmengdeRequest = DeltakelsesmengdeRequest(
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
            forslagId = null,
            deltakelsesprosent = 50,
            dagerPerUke = 2,
            begrunnelse = "begrunnelse",
            gyldigFra = LocalDate.now(),
        )

        val deltakelsesmengdeEndring = deltakelsesmengdeRequest.toEndring()
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = deltakelsesmengdeEndring)))
        val deltaker = TestData.lagDeltaker(
            deltakelsesprosent = deltakelsesmengdeEndring.deltakelsesprosent,
            dagerPerUke = deltakelsesmengdeEndring.dagerPerUke,
        )

        runEndringTest(deltakelsesmengdeRequest, deltaker, historikk)
    }

    @Test
    fun `post startdato - har tilgang - returnerer 200`() {
        val startdatoRequest = StartdatoRequest(
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
            forslagId = null,
            startdato = LocalDate.now().minusDays(2),
            sluttdato = LocalDate.now().plusMonths(2),
            begrunnelse = "begrunnelse",
        )

        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = startdatoRequest.toEndring())))
        val deltaker = TestData.lagDeltaker(startdato = startdatoRequest.startdato)

        runEndringTest(startdatoRequest, deltaker, historikk)
    }

    @Test
    fun `post sluttdato - har tilgang - returnerer 200`() {
        val sluttdatoRequest = SluttdatoRequest(
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
            forslagId = null,
            sluttdato = LocalDate.now().minusDays(2),
            begrunnelse = "begrunnelse",
        )

        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = sluttdatoRequest.toEndring())))
        val deltaker = TestData.lagDeltaker(sluttdato = sluttdatoRequest.sluttdato)

        runEndringTest(sluttdatoRequest, deltaker, historikk)
    }

    @Test
    fun `post sluttarsak - har tilgang - returnerer 200`() {
        val sluttarsakRequest = SluttarsakRequest(
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
            forslagId = null,
            aarsak = DeltakerEndring.Aarsak(
                type = DeltakerEndring.Aarsak.Type.ANNET,
                beskrivelse = "beskrivelse",
            ),
            begrunnelse = "begrunnelse",
        )

        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = sluttarsakRequest.toEndring())))
        val deltaker = TestData.lagDeltaker(
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
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
            forslagId = null,
            sluttdato = LocalDate.now().plusWeeks(2),
            begrunnelse = "begrunnelse",
        )

        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = forlengDeltakelseRequest.toEndring())))
        val deltaker = TestData.lagDeltaker(sluttdato = forlengDeltakelseRequest.sluttdato)

        runEndringTest(forlengDeltakelseRequest, deltaker, historikk)
    }

    @Test
    fun `post ikke aktuell - har tilgang - returnerer 200`() {
        val ikkeAktuellRequest = IkkeAktuellRequest(
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
            forslagId = null,
            aarsak = DeltakerEndring.Aarsak(
                type = DeltakerEndring.Aarsak.Type.IKKE_MOTT,
                beskrivelse = null,
            ),
            begrunnelse = "begrunnelse",
        )

        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = ikkeAktuellRequest.toEndring())))
        val deltaker = TestData.lagDeltaker(
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
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
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
        val deltaker = TestData.lagDeltaker(
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
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
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
        val deltaker = TestData.lagDeltaker(
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
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
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
            endretAv = TestData.randomIdent(),
            endretAvEnhet = TestData.randomEnhetsnummer(),
            forslagId = null,
            begrunnelse = "begrunnelse",
        )

        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = fjernOppstartsdatoRequest.toEndring())))
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )

        runEndringTest(fjernOppstartsdatoRequest, deltaker, historikk)
    }

    @Test
    fun `post sist-besokt - har tilgang - returnerer 200`() {
        val deltakerInTest = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )

        val sistBesoktInTest = ZonedDateTime.now()

        every { deltakerService.oppdaterSistBesokt(deltakerInTest.id, sistBesoktInTest) } just Runs

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${deltakerInTest.id}/sist-besokt") {
                postRequest(sistBesoktInTest)
            }

            response.status shouldBe HttpStatusCode.Companion.OK
        }

        verify(exactly = 1) {
            deltakerService.oppdaterSistBesokt(
                deltakerId = deltakerInTest.id,
                sistBesokt = sistBesoktInTest.withZoneSameInstant(ZoneOffset.UTC),
            )
        }
    }

    private fun runEndringTest(
        request: EndringRequest,
        deltaker: Deltaker,
        historikk: List<DeltakerHistorikk.Endring>,
    ) {
        coEvery { deltakerService.upsertEndretDeltaker(deltaker.id, request) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${deltaker.id}/endre-deltaker") {
                postRequest(request)
            }

            response.status shouldBe HttpStatusCode.Companion.OK
            response.bodyAsText() shouldBe
                objectMapper.writeValueAsString(DtoMappers.deltakerEndringResponseFromDeltaker(deltaker, historikk))
        }

        coVerify { deltakerService.upsertEndretDeltaker(deltaker.id, request) }
    }
}
