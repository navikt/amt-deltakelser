package no.nav.amt.deltaker.bff.veileder.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.TestData.lagNavAnsatteForDeltaker
import no.nav.amt.deltaker.bff.veileder.api.request.PameldingUtenGodkjenningRequest
import no.nav.amt.deltaker.bff.veileder.api.request.UtkastRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.noBodyRequest
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.objectMapper
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Test
import java.util.UUID

class PameldingApiTest : IntegrationTestBase() {
    @Test
    fun `meld på direkte og avbryt utkast - har ikke tilgang - returnerer 403`() {
        val deltaker = lagDeltakerResponse(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )

        coEvery { amtDeltakerClient.getDeltaker(any()) } returns deltaker
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(any()) } returns deltaker.navBruker.personident
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true

        withTestApplicationContext { httpClient ->
            httpClient
                .post("/pamelding/${UUID.randomUUID()}/utenGodkjenning") {
                    createPostRequest(
                        pameldingUtenGodkjenningRequest(
                            deltaker.deltakelsesinnhold!!.innhold.toInnholdDto(),
                        ),
                    )
                }.status shouldBe HttpStatusCode.Forbidden

            httpClient.post("/pamelding/${UUID.randomUUID()}/avbryt") { noBodyRequest() }.status shouldBe HttpStatusCode.Forbidden
        }
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { httpClient ->
            httpClient.post("/pamelding/${UUID.randomUUID()}/utenGodkjenning") { setBody("foo") }.status shouldBe
                HttpStatusCode.Unauthorized
            httpClient.post("/pamelding/${UUID.randomUUID()}/avbryt") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post utkast - har tilgang - oppretter utkast og returnerer deltaker`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))
        mockAnsatteOgEnhetForDeltaker(deltaker)
        val amtDeltakerResponse = lagDeltakerResponse(deltaker)
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        coEvery { amtDeltakerClient.getDeltaker(deltaker.id) } returns amtDeltakerResponse
        coEvery { paameldingClient.utkast(any()) } returns amtDeltakerResponse
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true

        withTestApplicationContext { httpClient ->
            httpClient
                .post(
                    "/pamelding/${deltaker.id}",
                ) { createPostRequest(utkastRequest(deltaker.deltakelsesinnhold!!.innhold.toInnholdDto())) }
                .apply {
                    status shouldBe HttpStatusCode.OK

                    // Routen bygger respons fra mock-svaret (amtDeltakerResponse), ikke det lokale
                    // `deltaker`-objektet. expected må derfor gå gjennom samme mapping:
                    // amt-deltaker DeltakerResponse -> DeltakerModel -> bff DeltakerResponse.
                    val expected = DeltakerResponse.fromDeltakerModel(
                        ModelMapper.toDeltaker(amtDeltakerResponse),
                    )

                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
        }
    }

    @Test
    fun `post utkast - deltaker finnes ikke - returnerer 404`() {
        coEvery { amtDeltakerClient.getDeltaker(any()) } throws NoSuchElementException()

        withTestApplicationContext { httpClient ->
            httpClient.post("/pamelding/${UUID.randomUUID()}") { createPostRequest(utkastRequest()) }.apply {
                status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `post utkast uten godkjenning - deltaker finnes ikke - returnerer 404`() {
        coEvery { paameldingClient.utkast(any()) } throws NoSuchElementException()
        coEvery { amtDeltakerClient.getDeltaker(any()) } throws NoSuchElementException()

        withTestApplicationContext { httpClient ->
            httpClient
                .post("/pamelding/${UUID.randomUUID()}/utenGodkjenning") { createPostRequest(pameldingUtenGodkjenningRequest()) }
                .apply {
                    status shouldBe HttpStatusCode.NotFound
                }
        }
    }

    @Test
    fun `avbryt utkast - har tilgang  - avbryter utkast og returnerer 200`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(any()) } returns deltaker.navBruker.personident
        coEvery { paameldingClient.avbrytUtkast(any(), any(), any()) } returns Unit

        withTestApplicationContext { httpClient ->
            httpClient.post("/pamelding/${deltaker.id}/avbryt") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    private fun utkastRequest(innhold: List<InnholdsElementRequest> = emptyList()) = UtkastRequest(innhold, "Bakgrunnen for...", null, null)

    private fun pameldingUtenGodkjenningRequest(innhold: List<InnholdsElementRequest> = emptyList()) = PameldingUtenGodkjenningRequest(
        innhold,
        "Bakgrunnen for...",
        null,
        null,
    )

    private fun mockAnsatteOgEnhetForDeltaker(deltaker: Deltaker): Pair<Map<UUID, NavAnsatt>, NavEnhet?> {
        val ansatte = lagNavAnsatteForDeltaker(deltaker).associateBy { it.id }
        val enhet = deltaker.vedtaksinformasjon?.let { lagNavEnhet(id = it.sistEndretAvEnhet) }

        every { navAnsattService.hentAnsatteForDeltaker(deltaker) } returns ansatte
        enhet?.let { every { navEnhetService.hentEnhet(it.id) } returns it }

        return Pair(ansatte, enhet)
    }

    companion object {
        private fun List<Innhold>.toInnholdDto() = this.map {
            InnholdsElementRequest(
                it.innholdskode,
                it.beskrivelse,
            )
        }
    }
}
