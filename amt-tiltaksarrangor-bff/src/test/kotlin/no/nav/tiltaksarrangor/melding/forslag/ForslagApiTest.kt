package no.nav.tiltaksarrangor.melding.forslag

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.tiltaksarrangor.IntegrationTestBase
import no.nav.tiltaksarrangor.melding.forslag.request.AvsluttDeltakelseRequest
import no.nav.tiltaksarrangor.melding.forslag.request.DeltakelsesmengdeRequest
import no.nav.tiltaksarrangor.melding.forslag.request.EndreAvslutningRequest
import no.nav.tiltaksarrangor.melding.forslag.request.FjernOppstartsdatoRequest
import no.nav.tiltaksarrangor.melding.forslag.request.ForlengDeltakelseRequest
import no.nav.tiltaksarrangor.melding.forslag.request.ForslagRequest
import no.nav.tiltaksarrangor.melding.forslag.request.IkkeAktuellRequest
import no.nav.tiltaksarrangor.melding.forslag.request.SluttarsakRequest
import no.nav.tiltaksarrangor.melding.forslag.request.SluttdatoRequest
import no.nav.tiltaksarrangor.melding.forslag.request.StartdatoRequest
import no.nav.tiltaksarrangor.testutils.DbTestDataUtils.shouldBeCloseTo
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@AutoConfigureMockMvc
class ForslagApiTest(
    private val forslagService: ForslagService,
    private val mockMvc: MockMvc,
) : IntegrationTestBase() {
    @Test
    fun `skal teste token autentisering`() {
        val deltakerId = UUID.randomUUID()

        listOf(
            "/tiltaksarrangor/deltaker/$deltakerId/forslag/forleng",
            "/tiltaksarrangor/deltaker/$deltakerId/forslag/avslutt",
            "/tiltaksarrangor/deltaker/$deltakerId/forslag/ikke-aktuell",
            "/tiltaksarrangor/deltaker/$deltakerId/forslag/deltakelsesmengde",
            "/tiltaksarrangor/deltaker/$deltakerId/forslag/startdato",
            "/tiltaksarrangor/deltaker/$deltakerId/forslag/sluttarsak",
            "/tiltaksarrangor/deltaker/$deltakerId/forslag/fjern-oppstartsdato",
            "/tiltaksarrangor/deltaker/$deltakerId/forslag/${UUID.randomUUID()}/tilbakekall",
        ).forEach { path ->
            mockMvc
                .post(path) {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(forlengDeltakelseRequest)
                }.andExpect { status { isUnauthorized() } }
        }
    }

    @Test
    fun `forslag - har ikke tilgang til deltakerliste - skal returnere 403`() {
        requests.forEach { request ->
            with(DeltakerContext(applicationContext)) {
                setKoordinatorDeltakerliste(UUID.randomUUID())

                mockMvc
                    .post("/tiltaksarrangor/deltaker/${deltaker.id}/forslag/${getPathForRequest(request)}") {
                        contentType = MediaType.APPLICATION_JSON
                        content = objectMapper.writeValueAsString(request)
                        header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                    }.andExpect { status { isForbidden() } }
            }
        }

        with(DeltakerContext(applicationContext)) {
            setKoordinatorDeltakerliste(UUID.randomUUID())

            mockMvc
                .post("/tiltaksarrangor/deltaker/${deltaker.id}/forslag/${UUID.randomUUID()}/tilbakekall") {
                    contentType = MediaType.APPLICATION_JSON
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                }.andExpect { status { isForbidden() } }
        }
    }

    @Test
    fun `forslag - deltaker adressebeskyttet, ansatt er ikke veileder - skal returnere 403`() {
        requests.forEach { request ->
            with(DeltakerContext(applicationContext)) {
                setDeltakerAdressebeskyttet()

                mockMvc
                    .post("/tiltaksarrangor/deltaker/${deltaker.id}/forslag/${getPathForRequest(request)}") {
                        contentType = MediaType.APPLICATION_JSON
                        content = objectMapper.writeValueAsString(request)
                        header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                    }.andExpect { status { isForbidden() } }
            }
        }

        with(DeltakerContext(applicationContext)) {
            setDeltakerAdressebeskyttet()

            mockMvc
                .post("/tiltaksarrangor/deltaker/${deltaker.id}/forslag/${UUID.randomUUID()}/tilbakekall") {
                    contentType = MediaType.APPLICATION_JSON
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                }.andExpect { status { isForbidden() } }
        }
    }

    @Test
    fun `forslag - deltaker skjult - skal returnere 400`() {
        requests.forEach { request ->
            with(DeltakerContext(applicationContext)) {
                setDeltakerSkjult()

                mockMvc
                    .post("/tiltaksarrangor/deltaker/${deltaker.id}/forslag/${getPathForRequest(request)}") {
                        contentType = MediaType.APPLICATION_JSON
                        content = objectMapper.writeValueAsString(request)
                        header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                    }.andExpect { status { isBadRequest() } }
            }
        }

        with(DeltakerContext(applicationContext)) {
            setDeltakerSkjult()

            mockMvc
                .post("/tiltaksarrangor/deltaker/${deltaker.id}/forslag/${UUID.randomUUID()}/tilbakekall") {
                    contentType = MediaType.APPLICATION_JSON
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                }.andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `forleng - nytt forslag - skal returnere 200 og riktig response`() {
        testOpprettetForslag(forlengDeltakelseRequest) { endring ->
            endring as Forslag.ForlengDeltakelse
            endring.sluttdato shouldBe forlengDeltakelseRequest.sluttdato
        }
    }

    @Test
    fun `avslutt - nytt forslag - skal returnere 200 og riktig response`() {
        testOpprettetForslag(avsluttDeltakelseRequest) { endring ->
            endring as Forslag.AvsluttDeltakelse
            endring.sluttdato shouldBe avsluttDeltakelseRequest.sluttdato
            endring.aarsak shouldBe avsluttDeltakelseRequest.aarsak
        }
    }

    @Test
    fun `ikke-aktuell - nytt forslag - skal returnere 200 og riktig response`() {
        testOpprettetForslag(ikkeAktuellRequest) { endring ->
            endring as Forslag.IkkeAktuell
            endring.aarsak shouldBe avsluttDeltakelseRequest.aarsak
        }
    }

    @Test
    fun `deltakelsesmengde - nytt forslag - skal returnere 200 og riktig response`() {
        testOpprettetForslag(deltakelsesmengdeRequest) { endring ->
            endring as Forslag.Deltakelsesmengde
            endring.deltakelsesprosent shouldBe deltakelsesmengdeRequest.deltakelsesprosent
            endring.dagerPerUke shouldBe deltakelsesmengdeRequest.dagerPerUke
        }
    }

    @Test
    fun `sluttdato - nytt forslag - skal returnere 200 og riktig response`() {
        testOpprettetForslag(sluttdatoRequest) { endring ->
            endring as Forslag.Sluttdato
            endring.sluttdato shouldBe sluttdatoRequest.sluttdato
        }
    }

    @Test
    fun `startdato - nytt forslag - skal returnere 200 og riktig response`() {
        testOpprettetForslag(startdatoRequest) { endring ->
            endring as Forslag.Startdato
            endring.startdato shouldBe startdatoRequest.startdato
            endring.sluttdato shouldBe startdatoRequest.sluttdato
        }
    }

    @Test
    fun `sluttarsak - nytt forslag - skal returnere 200 og riktig response`() {
        testOpprettetForslag(sluttarsakRequest) { endring ->
            endring as Forslag.Sluttarsak
            endring.aarsak shouldBe sluttarsakRequest.aarsak
        }
    }

    @Test
    fun `fjern-oppstartsdato - nytt forslag - skal returnere 200 og riktig response`() {
        testOpprettetForslag(fjernOppstartdatoRequest) { endring ->
            endring as Forslag.FjernOppstartsdato
        }
    }

    @Test
    fun `tilbakekall - aktivt forslag - skal returnere 200`() {
        with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
            upsertForslag()

            mockMvc
                .post("/tiltaksarrangor/deltaker/${deltaker.id}/forslag/${forslag.id}/tilbakekall") {
                    contentType = MediaType.APPLICATION_JSON
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                }.andExpect { status { isOk() } }

            forslagService.get(forslag.id).isFailure shouldBe true
        }
    }

    private fun testOpprettetForslag(
        request: ForslagRequest,
        block: (endring: Forslag.Endring) -> Unit,
    ) {
        with(DeltakerContext(applicationContext)) {
            val response = mockMvc
                .post("/tiltaksarrangor/deltaker/${deltaker.id}/forslag/${getPathForRequest(request)}") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                }.andExpect { status { isOk() } }
                .andReturn()
                .response

            val aktivtForslag = objectMapper.readValue<AktivtForslagResponse>(response.contentAsString)

            assertSoftly(aktivtForslag) {
                status shouldBe ForslagResponse.Status.VenterPaSvar
                begrunnelse shouldBe request.begrunnelse
                opprettet shouldBeCloseTo LocalDateTime.now()
            }

            block(aktivtForslag.endring)
        }
    }

    companion object {
        private val forlengDeltakelseRequest = ForlengDeltakelseRequest(LocalDate.now().plusWeeks(42), "Forlengelse fordi...")
        private val avsluttDeltakelseRequest =
            AvsluttDeltakelseRequest(LocalDate.now().plusWeeks(1), EndringAarsak.FattJobb, "Avslutning fordi...", false, null)
        private val ikkeAktuellRequest = IkkeAktuellRequest(EndringAarsak.FattJobb, "Ikke aktuell fordi...")
        private val deltakelsesmengdeRequest = DeltakelsesmengdeRequest(42, 3, LocalDate.now(), "Deltakelsesmengde fordi...")
        private val sluttdatoRequest = SluttdatoRequest(LocalDate.now().plusWeeks(42), "Endres fordi...")
        private val startdatoRequest = StartdatoRequest(LocalDate.now(), LocalDate.now().plusWeeks(4), begrunnelse = "Startdato fordi...")
        private val sluttarsakRequest = SluttarsakRequest(EndringAarsak.Utdanning, begrunnelse = "Sluttårsak fordi...")
        private val fjernOppstartdatoRequest = FjernOppstartsdatoRequest("begrunnelse")

        private val requests = listOf(
            forlengDeltakelseRequest,
            avsluttDeltakelseRequest,
            ikkeAktuellRequest,
            deltakelsesmengdeRequest,
            sluttdatoRequest,
            startdatoRequest,
            sluttarsakRequest,
            fjernOppstartdatoRequest,
        )

        private fun getPathForRequest(request: ForslagRequest): String = when (request) {
            is AvsluttDeltakelseRequest -> "avslutt"
            is ForlengDeltakelseRequest -> "forleng"
            is IkkeAktuellRequest -> "ikke-aktuell"
            is DeltakelsesmengdeRequest -> "deltakelsesmengde"
            is SluttdatoRequest -> "sluttdato"
            is SluttarsakRequest -> "sluttarsak"
            is StartdatoRequest -> "startdato"
            is FjernOppstartsdatoRequest -> "fjern-oppstartsdato"
            is EndreAvslutningRequest -> "endre-avslutning"
        }
    }
}
