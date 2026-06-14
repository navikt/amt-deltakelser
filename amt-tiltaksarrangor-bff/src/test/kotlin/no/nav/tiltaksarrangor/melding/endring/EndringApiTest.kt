package no.nav.tiltaksarrangor.melding.endring

import io.kotest.matchers.shouldBe
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.melding.endring.request.LeggTilOppstartsdatoRequest
import no.nav.tiltaksarrangor.model.Deltaker
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.util.UUID

@AutoConfigureMockMvc
class EndringApiTest(
    private val mockMvc: MockMvc,
) : IntegrationTest() {
    private val leggTilOppstartsdatoRequest = LeggTilOppstartsdatoRequest(LocalDate.now(), LocalDate.now().plusMonths(3))

    @Test
    fun `skal teste token autentisering`() {
        val deltakerId = UUID.randomUUID()
        mockMvc
            .post("/tiltaksarrangor/deltaker/$deltakerId/endring/legg-til-oppstartsdato") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(leggTilOppstartsdatoRequest)
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `endring - har ikke tilgang til deltakerliste - skal returnere 403`() {
        with(DeltakerContext(applicationContext)) {
            setKoordinatorDeltakerliste(UUID.randomUUID())

            mockMvc
                .post("/tiltaksarrangor/deltaker/${deltaker.id}/endring/legg-til-oppstartsdato") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(leggTilOppstartsdatoRequest)
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                }.andExpect { status { isForbidden() } }
        }
    }

    @Test
    fun `endring - deltaker adressebeskyttet, ansatt er ikke veileder - skal returnere 403`() {
        with(DeltakerContext(applicationContext)) {
            setDeltakerAdressebeskyttet()

            mockMvc
                .post("/tiltaksarrangor/deltaker/${deltaker.id}/endring/legg-til-oppstartsdato") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(leggTilOppstartsdatoRequest)
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                }.andExpect { status { isForbidden() } }
        }
    }

    @Test
    fun `endring - deltaker skjult - skal returnere 400`() {
        with(DeltakerContext(applicationContext)) {
            setDeltakerSkjult()

            mockMvc
                .post("/tiltaksarrangor/deltaker/${deltaker.id}/endring/legg-til-oppstartsdato") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(leggTilOppstartsdatoRequest)
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                }.andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `startdato - ny endring - skal returnere 200 og riktig response`() {
        with(DeltakerContext(applicationContext)) {
            setVenterPaOppstart()

            val response = mockMvc
                .post("/tiltaksarrangor/deltaker/${deltaker.id}/endring/legg-til-oppstartsdato") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(leggTilOppstartsdatoRequest)
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = koordinator.personIdent)}")
                }.andExpect { status { isOk() } }
                .andReturn()
                .response

            val responseBody = objectMapper.readValue<Deltaker>(response.contentAsString)

            responseBody.startDato shouldBe leggTilOppstartsdatoRequest.startdato
            responseBody.sluttDato shouldBe leggTilOppstartsdatoRequest.sluttdato
        }
    }
}
