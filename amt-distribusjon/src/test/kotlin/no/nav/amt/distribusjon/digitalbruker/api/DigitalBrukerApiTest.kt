package no.nav.amt.distribusjon.digitalbruker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.coEvery
import no.nav.amt.distribusjon.IntegrationTestBase
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.utils.generateJWT
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DigitalBrukerApiTest : IntegrationTestBase() {
    private val personident = randomIdent()

    @BeforeEach
    fun setupMocks() {
        coEvery { dokdistkanalClient.bestemDistribusjonskanal(personident) } returns Distribusjonskanal.SDP
        coEvery { veilarboppfolgingClient.erUnderManuellOppfolging(personident) } returns false
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.post("/digital") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post digital - ikke manuell oppfolging, SDP - bruker er digital`() {
        withTestApplicationContext { client ->
            client.post("/digital") { postRequest(DigitalBrukerRequest(personident)) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(DigitalBrukerResponse(true))
            }
        }
    }

    @Test
    fun `post digital - ikke manuell oppfolging, print - bruker er ikke digital`() {
        withTestApplicationContext { client ->
            coEvery { dokdistkanalClient.bestemDistribusjonskanal(personident) } returns Distribusjonskanal.PRINT

            client.post("/digital") { postRequest(DigitalBrukerRequest(personident)) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(DigitalBrukerResponse(false))
            }
        }
    }

    @Test
    fun `post digital - manuell oppfolging, SDP - bruker er ikke digital`() {
        coEvery { veilarboppfolgingClient.erUnderManuellOppfolging(personident) } returns true

        withTestApplicationContext { client ->
            client.post("/digital") { postRequest(DigitalBrukerRequest(personident)) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(DigitalBrukerResponse(false))
            }
        }
    }

    @Test
    fun `post digital - veilarboppfolging feiler - returnerer 500`() {
        withTestApplicationContext { client ->
            coEvery { veilarboppfolgingClient.erUnderManuellOppfolging(personident) } throws
                IllegalStateException("Feil ved henting av manuell oppfølging")

            client.post("/digital") { postRequest(DigitalBrukerRequest(personident)) }.apply {
                status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    @Test
    fun `post digital - dokdistkanal feiler - returnerer 500`() {
        withTestApplicationContext { client ->
            coEvery { dokdistkanalClient.bestemDistribusjonskanal(personident) } throws
                IllegalStateException("Kunne ikke hente distribusjonskanal")

            client.post("/digital") { postRequest(DigitalBrukerRequest(personident)) }.apply {
                status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    companion object {
        private fun HttpRequestBuilder.postRequest(body: Any) {
            header(
                HttpHeaders.Authorization,
                "Bearer ${
                    generateJWT(
                        consumerClientId = "amt-deltaker-bff",
                        audience = "amt-distribusjon",
                    )
                }",
            )
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(body))
        }
    }
}
