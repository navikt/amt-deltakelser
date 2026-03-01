package no.nav.amt.distribusjon.digitalbruker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.integrationTest
import no.nav.amt.distribusjon.utils.MockResponseHandler
import no.nav.amt.distribusjon.utils.data.randomIdent
import no.nav.amt.distribusjon.utils.postRequest
import org.junit.jupiter.api.Test

class DigitalBrukerApiTest {
    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = integrationTest { _, client ->
        client.post("/digital") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `post digital - ikke manuell oppfolging, SDP - bruker er digital`() = integrationTest { _, client ->
        val personident = randomIdent()
        MockResponseHandler.addDistribusjonskanalResponse(personident, Distribusjonskanal.SDP)
        MockResponseHandler.addManuellOppfolgingResponse(personident, false)
        client.post("/digital") { postRequest(DigitalBrukerRequest(personident)) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(DigitalBrukerResponse(true))
        }
    }

    @Test
    fun `post digital - ikke manuell oppfolging, print - bruker er ikke digital`() = integrationTest { _, client ->
        val personident = randomIdent()
        MockResponseHandler.addDistribusjonskanalResponse(personident, Distribusjonskanal.PRINT)
        MockResponseHandler.addManuellOppfolgingResponse(personident, false)
        client.post("/digital") { postRequest(DigitalBrukerRequest(personident)) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(DigitalBrukerResponse(false))
        }
    }

    @Test
    fun `post digital - manuell oppfolging, SDP - bruker er ikke digital`() = integrationTest { _, client ->
        val personident = randomIdent()
        MockResponseHandler.addDistribusjonskanalResponse(personident, Distribusjonskanal.SDP)
        MockResponseHandler.addManuellOppfolgingResponse(personident, true)
        client.post("/digital") { postRequest(DigitalBrukerRequest(personident)) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(DigitalBrukerResponse(false))
        }
    }

    @Test
    fun `post digital - veilarboppfolging feiler - returnerer 500`() = integrationTest { _, client ->
        val personident = randomIdent()
        MockResponseHandler.addDistribusjonskanalResponse(personident, Distribusjonskanal.SDP)
        MockResponseHandler.addManuellOppfolgingErrorResponse(personident, HttpStatusCode.InternalServerError)
        client.post("/digital") { postRequest(DigitalBrukerRequest(personident)) }.apply {
            status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `post digital - dokdistkanal feiler - returnerer 500`() = integrationTest { _, client ->
        val personident = randomIdent()
        MockResponseHandler.addDistribusjonskanalErrorResponse(personident, HttpStatusCode.InternalServerError)
        MockResponseHandler.addManuellOppfolgingResponse(personident, false)
        client.post("/digital") { postRequest(DigitalBrukerRequest(personident)) }.apply {
            status shouldBe HttpStatusCode.InternalServerError
        }
    }
}
