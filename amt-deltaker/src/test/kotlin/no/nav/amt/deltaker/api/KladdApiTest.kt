package no.nav.amt.deltaker.api

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
import io.mockk.just
import io.mockk.mockk
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.application.plugins.OpprettKladdRequestValidator
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.veileder.KladdService
import no.nav.amt.internapi.paamelding.request.OpprettKladdRequest
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.util.UUID

class KladdApiTest : IntegrationTestBase() {
    override val kladdService = mockk<KladdService>()
    override val deltakerResponseBuilder = mockk<DeltakerResponseBuilder>()
    override val opprettKladdRequestValidator = mockk<OpprettKladdRequestValidator>()

    @Test
    fun `post - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.post("/kladd") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            client.delete("/kladd/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post kladd - request med valideringsfeil - returnerer 400 BadRequest`() {
        coEvery {
            opprettKladdRequestValidator.validateRequest(any())
        } returns ValidationResult.Invalid(listOf("~some error~", "~some other error~"))

        withTestApplicationContext<Unit> { client ->
            val response = client.post("/kladd") {
                postRequest(opprettKladdRequest)
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldContain ("~some error~, ~some other error~")
        }
    }

    @Test
    fun `post kladd - har tilgang - returnerer deltaker`() {
        val deltaker = TestData.lagDeltaker()
        val deltakerResponse = TestData.lagDeltakerResponse(deltaker)

        coEvery { opprettKladdRequestValidator.validateRequest(any()) } returns ValidationResult.Valid
        coEvery { kladdService.opprettKladd(any<UUID>(), any()) } returns deltaker
        coEvery { deltakerResponseBuilder.buildDeltakerResponse(any(), any()) } returns deltakerResponse

        withTestApplicationContext { client ->
            val response = client.post("/kladd") {
                postRequest(opprettKladdRequest)
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe objectMapper.writeValueAsString(deltakerResponse)
        }
    }

    @Test
    fun `post kladd - deltakerliste finnes ikke - returnerer 404`() {
        coEvery { opprettKladdRequestValidator.validateRequest(any()) } returns ValidationResult.Valid
        coEvery { kladdService.opprettKladd(any<UUID>(), any()) } throws NoSuchElementException("Fant ikke deltakerliste")

        withTestApplicationContext { client ->
            val response = client.post("/kladd") {
                postRequest(opprettKladdRequest)
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `delete kladd - har tilgang - returnerer 200`() {
        val deltakerId = UUID.randomUUID()
        coEvery { kladdService.slettKladd(deltakerId) } just Runs

        withTestApplicationContext { client ->
            client.delete("/kladd/$deltakerId") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `post kladd-og-deltaker - slett lyktes - returnerer true`() {
        val deltakerId = UUID.randomUUID()
        coEvery { kladdService.slettKladd(deltakerId) } just Runs

        withTestApplicationContext { client ->
            client.post("/kladd-og-deltaker/$deltakerId") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
                val body = objectMapper.readValue(bodyAsText(), Map::class.java)
                body["slettet"] shouldBe true
            }
        }
    }

    @Test
    fun `post kladd-og-deltaker - slett feiler - returnerer false`() {
        val deltakerId = UUID.randomUUID()
        coEvery { kladdService.slettKladd(deltakerId) } throws IllegalArgumentException("Kan ikke slette deltaker")

        withTestApplicationContext { client ->
            client.post("/kladd-og-deltaker/$deltakerId") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
                val body = objectMapper.readValue(bodyAsText(), Map::class.java)
                body["slettet"] shouldBe false
            }
        }
    }

    companion object {
        private val opprettKladdRequest = OpprettKladdRequest(UUID.randomUUID(), "1234")
    }
}
