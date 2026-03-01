package no.nav.amt.deltaker.deltaker.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import no.nav.amt.deltaker.deltaker.api.DtoMappers.opprettKladdResponseFromDeltaker
import no.nav.amt.deltaker.deltaker.api.DtoMappers.utkastResponseFromDeltaker
import no.nav.amt.deltaker.deltaker.api.utils.noBodyRequest
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.OpprettKladdRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.UtkastRequest
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.util.UUID

class PameldingApiTest : RouteTestBase() {
    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.post("/pamelding") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("/pamelding/${UUID.randomUUID()}") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.post("/pamelding/${UUID.randomUUID()}/innbygger/godkjenn-utkast") { setBody("foo") }.status shouldBe
                HttpStatusCode.Unauthorized
            client.post("/pamelding/${UUID.randomUUID()}/avbryt") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.delete("/pamelding/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post pamelding - request med valideringsfeil - returnerer 400 BadRequest`() {
        coEvery {
            opprettKladdRequestValidator.validateRequest(any())
        } returns ValidationResult.Invalid(listOf("~some error~", "~some other error~"))

        withTestApplicationContext<Unit> { client ->
            val response = client.post("/pamelding") {
                postRequest(opprettKladdRequest)
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldContain ("~some error~, ~some other error~")
        }
    }

    @Test
    fun `post pamelding - har tilgang - returnerer deltaker`() {
        val deltaker = lagDeltaker()

        coEvery { opprettKladdRequestValidator.validateRequest(any()) } returns ValidationResult.Valid
        coEvery { pameldingService.opprettDeltaker(any(), any()) } returns deltaker

        withTestApplicationContext { client ->
            val response = client.post("/pamelding") {
                postRequest(opprettKladdRequest)
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe objectMapper.writeValueAsString(opprettKladdResponseFromDeltaker(deltaker))
        }
    }

    @Test
    fun `post pamelding - deltakerliste finnes ikke - returnerer 404`() {
        coEvery { opprettKladdRequestValidator.validateRequest(any()) } returns ValidationResult.Valid
        coEvery { pameldingService.opprettDeltaker(any(), any()) } throws NoSuchElementException("Fant ikke deltakerliste")

        withTestApplicationContext { client ->
            val response = client.post("/pamelding") {
                postRequest(opprettKladdRequest)
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `post pamelding utkast - har tilgang - returnerer 200`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING))
        val historikk: List<DeltakerHistorikk> = listOf(DeltakerHistorikk.Vedtak(TestData.lagVedtak(deltakerVedVedtak = deltaker)))

        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk
        coEvery { pameldingService.upsertUtkast(deltaker.id, any()) } returns deltaker

        withTestApplicationContext { client ->
            client
                .post("/pamelding/${deltaker.id}") {
                    postRequest(utkastRequest)
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(utkastResponseFromDeltaker(deltaker, historikk))
                }
        }
    }

    @Test
    fun `post pamelding utkast - deltaker finnes ikke - returnerer 404`() {
        val deltakerId = UUID.randomUUID()
        coEvery { pameldingService.upsertUtkast(deltakerId, any()) } throws NoSuchElementException("Fant ikke deltaker")

        withTestApplicationContext { client ->
            client
                .post("/pamelding/$deltakerId") {
                    postRequest(utkastRequest)
                }.apply {
                    status shouldBe HttpStatusCode.NotFound
                }
        }
    }

    @Test
    fun `post avbryt utkast - har tilgang - returnerer 200`() {
        val deltakerId = UUID.randomUUID()
        coEvery { pameldingService.avbrytUtkast(deltakerId, any()) } just Runs

        withTestApplicationContext { client ->
            client
                .post("/pamelding/$deltakerId/avbryt") {
                    postRequest(avbrytUtkastRequest)
                }.apply {
                    status shouldBe HttpStatusCode.OK
                }
        }
    }

    @Test
    fun `delete kladd - har tilgang - returnerer 200`() {
        val deltakerId = UUID.randomUUID()
        coEvery { pameldingService.slettKladd(deltakerId) } just Runs

        withTestApplicationContext { client ->
            client.delete("/pamelding/$deltakerId") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    companion object {
        private val opprettKladdRequest = OpprettKladdRequest(UUID.randomUUID(), "1234")
        private val avbrytUtkastRequest = AvbrytUtkastRequest("Z123456", "0101")

        private val utkastRequest = UtkastRequest(
            Deltakelsesinnhold("test", listOf(Innhold("Tekst", "kode", true, null))),
            "bakgrunn og sånn",
            50F,
            3F,
            "Z123456",
            "0101",
            false,
        )
    }
}
