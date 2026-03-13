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
import no.nav.amt.api.paamelding.request.OpprettKladdRequest
import no.nav.amt.deltaker.deltaker.api.DtoMappers
import no.nav.amt.deltaker.deltaker.api.utils.noBodyRequest
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.util.UUID

class KladdApiTest : RouteTestBase() {
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

        coEvery { opprettKladdRequestValidator.validateRequest(any()) } returns ValidationResult.Valid
        coEvery { kladdService.opprettKladd(any<UUID>(), any()) } returns deltaker

        withTestApplicationContext { client ->
            val response = client.post("/kladd") {
                postRequest(opprettKladdRequest)
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe objectMapper.writeValueAsString(
                DtoMappers.opprettKladdResponseFromDeltaker(
                    deltaker,
                ),
            )
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

    companion object {
        private val opprettKladdRequest = OpprettKladdRequest(UUID.randomUUID(), "1234")
    }
}
