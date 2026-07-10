package no.nav.amt.deltaker.bff.veileder.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerOld
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.veileder.api.request.OpprettKladdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.UtkastRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.noBodyRequest
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.internapi.paamelding.request.KladdRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.utils.objectMapper
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class KladdApiTest : IntegrationTestBase() {
    private val deltakerInTest = lagDeltakerOld()
    private val deltakerResponseInTest = TestData.lagDeltakerResponse(deltakerInTest)

    @BeforeEach
    fun setup() {
        coEvery {
            amtDeltakerClient.getPersonidentForDeltaker(any())
        } returns PersonIdentResponse(deltakerInTest.navBruker.personident).personident
    }

    @Test
    fun `post kladd - har tilgang - returnerer deltaker`() {
        coEvery { pameldingService.opprettKladd(any(), any()) } returns deltakerResponseInTest
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true

        withTestApplicationContext { httpClient ->
            httpClient.post("/kladd") { createPostRequest(opprettKladdRequest) }.apply {
                assertEquals(HttpStatusCode.OK, status)

                val expected = DeltakerResponse.fromDeltakerModel(
                    ModelMapper.toDeltaker(deltakerResponseInTest),
                )

                bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
            }
        }
    }

    @Test
    fun `post - har ikke tilgang - returnerer 403`() {
        // Arrange
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )
        every { deltakerRepository.get(any()) } returns Result.success(
            deltakerInTest.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            ),
        )
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true

        withTestApplicationContext { httpClient ->
            httpClient.post("/kladd") { createPostRequest(opprettKladdRequest) }.status shouldBe HttpStatusCode.Forbidden

            httpClient
                .post("/kladd/${UUID.randomUUID()}") {
                    createPostRequest(utkastRequest(deltakerInTest.deltakelsesinnhold!!.innhold.toInnholdDto()))
                }.status shouldBe HttpStatusCode.Forbidden

            httpClient.post("/kladd/${UUID.randomUUID()}") { createPostRequest(kladdRequest) }.status shouldBe HttpStatusCode.Forbidden

            httpClient.delete("/kladd/${UUID.randomUUID()}") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
        }
    }

    @Test
    fun `post kladd - har tilgang - returnerer 200`() {
        // Arrange
        val deltaker = deltakerInTest.copy(
            status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
        )

        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        coEvery { paameldingClient.oppdaterKladd(deltakerInTest.id, any()) } returns mockk<HttpResponse> {
            every { status } returns HttpStatusCode.OK
        }

        // Act & Assert
        withTestApplicationContext { httpClient ->
            httpClient.post("/kladd/${deltaker.id}") { createPostRequest(kladdRequest) }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `post kladd - feil deltakerstatus - returnerer 400`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        coEvery { paameldingClient.oppdaterKladd(any(), any()) } throws IllegalArgumentException("foo")

        withTestApplicationContext { httpClient ->
            httpClient.post("/kladd/${deltakerInTest.id}") { createPostRequest(kladdRequest) }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `slett kladd - deltaker er KLADD - sletter deltaker og returnerer 200`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        coEvery { paameldingClient.slettKladdOgDeltaker(deltakerInTest.id) } returns true

        withTestApplicationContext { httpClient ->
            httpClient.delete("/kladd/${deltakerInTest.id}") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `post - mangler token - returnerer 401`() {
        withTestApplicationContext { httpClient ->
            httpClient.post("/kladd") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            httpClient.post("/kladd/${UUID.randomUUID()}") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            httpClient.post("/kladd/${UUID.randomUUID()}") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            httpClient.delete("/kladd/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post kladd - deltakerliste finnes ikke - returnerer 404`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        coEvery {
            pameldingService.opprettKladd(any(), any())
        } throws NoSuchElementException("Deltaker ikke funnet")

        val response = withTestApplicationContext { httpClient ->
            httpClient.post("/kladd") { createPostRequest(opprettKladdRequest) }
        }

        response.status shouldBe HttpStatusCode.NotFound
    }

    companion object {
        private val kladdRequest = KladdRequest(
            innhold = emptyList(),
            bakgrunnsinformasjon = "Bakgrunnen for...",
            deltakelsesprosent = null,
            dagerPerUke = null,
        )

        private fun utkastRequest(innhold: List<InnholdsElementRequest> = emptyList()) = UtkastRequest(
            innhold = innhold,
            bakgrunnsinformasjon = "Bakgrunnen for...",
            deltakelsesprosent = null,
            dagerPerUke = null,
        )

        private val opprettKladdRequest = OpprettKladdRequest(
            deltakerlisteId = UUID.randomUUID(),
            personident = "1234",
        )

        private fun List<Innhold>.toInnholdDto() = this.map {
            InnholdsElementRequest(
                it.innholdskode,
                it.beskrivelse,
            )
        }
    }
}
