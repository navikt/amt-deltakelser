package no.nav.amt.deltaker.bff.veileder.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.utils.RouteTestBase
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavAnsatteForDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.bff.veileder.api.request.InnholdRequest
import no.nav.amt.deltaker.bff.veileder.api.request.PameldingUtenGodkjenningRequest
import no.nav.amt.deltaker.bff.veileder.api.request.UtkastRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.noBodyRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.objectMapper
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Test
import java.util.UUID

class PameldingApiTest : RouteTestBase() {
    @Test
    fun `get - har ikke tilgang - returnerer 403`() = runTest {
        val deltaker = lagDeltaker()
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )
        every { deltakerRepository.get(any()) } returns Result.success(
            lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            ),
        )
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
    fun `skal teste autentisering - mangler token - returnerer 401`() = runTest {
        withTestApplicationContext { httpClient ->
            httpClient.post("/pamelding/${UUID.randomUUID()}/utenGodkjenning") { setBody("foo") }.status shouldBe
                HttpStatusCode.Unauthorized
            httpClient.post("/pamelding/${UUID.randomUUID()}/avbryt") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post utkast - har tilgang - oppretter utkast og returnerer deltaker`() = runTest {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        coEvery { pameldingService.upsertUtkast(any()) } returns deltaker
        every { forslagRepository.getForDeltaker(deltaker.id) } returns emptyList()
        val (ansatte, enhet) = mockAnsatteOgEnhetForDeltaker(deltaker)

        withTestApplicationContext { httpClient ->
            httpClient
                .post(
                    "/pamelding/${deltaker.id}",
                ) { createPostRequest(utkastRequest(deltaker.deltakelsesinnhold!!.innhold.toInnholdDto())) }
                .apply {
                    status shouldBe HttpStatusCode.OK

                    val expected = DeltakerResponse.fromDeltaker(
                        deltaker = deltaker,
                        ansatte = ansatte,
                        vedtakSistEndretAvEnhet = enhet,
                        digitalBruker = true,
                        forslag = emptyList(),
                    )

                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
        }
    }

    @Test
    fun `post utkast - deltaker finnes ikke - returnerer 404`() = runTest {
        every { deltakerRepository.get(any()) } throws NoSuchElementException()

        withTestApplicationContext { httpClient ->
            httpClient.post("/pamelding/${UUID.randomUUID()}") { createPostRequest(utkastRequest()) }.apply {
                status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `post utkast uten godkjenning - har tilgang - oppretter og returnerer ferdig godkjent deltaker`() = runTest {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING))

        every { deltakerRepository.get(any()) } returns Result.success(deltaker)
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        coEvery { pameldingService.upsertUtkast(any()) } returns deltaker
        every { forslagRepository.getForDeltaker(deltaker.id) } returns emptyList()

        val (ansatte, enhet) = mockAnsatteOgEnhetForDeltaker(deltaker)

        withTestApplicationContext { httpClient ->
            httpClient
                .post(
                    "/pamelding/${deltaker.id}",
                ) { createPostRequest(utkastRequest(deltaker.deltakelsesinnhold!!.innhold.toInnholdDto())) }
                .apply {
                    status shouldBe HttpStatusCode.OK

                    val expected = DeltakerResponse.fromDeltaker(
                        deltaker = deltaker,
                        ansatte = ansatte,
                        vedtakSistEndretAvEnhet = enhet,
                        digitalBruker = true,
                        forslag = emptyList(),
                    )

                    bodyAsText() shouldBe objectMapper.writeValueAsString(expected)
                }
        }
    }

    @Test
    fun `post utkast uten godkjenning - deltaker finnes ikke - returnerer 404`() = runTest {
        every { deltakerRepository.get(any()) } throws NoSuchElementException()

        withTestApplicationContext { httpClient ->
            httpClient
                .post("/pamelding/${UUID.randomUUID()}/utenGodkjenning") { createPostRequest(pameldingUtenGodkjenningRequest()) }
                .apply {
                    status shouldBe HttpStatusCode.NotFound
                }
        }
    }

    @Test
    fun `avbryt utkast - har tilgang  - avbryter utkast og returnerer 200`() = runTest {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)
        coEvery { pameldingService.avbrytUtkast(deltaker, any(), any()) } returns Unit

        withTestApplicationContext { httpClient ->
            httpClient.post("/pamelding/${deltaker.id}/avbryt") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    private fun mockAnsatteOgEnhetForDeltaker(deltaker: Deltaker): Pair<Map<UUID, NavAnsatt>, NavEnhet?> {
        val ansatte = lagNavAnsatteForDeltaker(deltaker).associateBy { it.id }
        val enhet = deltaker.vedtaksinformasjon?.let { lagNavEnhet(id = it.sistEndretAvEnhet) }

        every { navAnsattService.hentAnsatteForDeltaker(deltaker) } returns ansatte
        enhet?.let { every { navEnhetService.hentEnhet(it.id) } returns it }

        return Pair(ansatte, enhet)
    }

    companion object {
        private fun utkastRequest(innhold: List<InnholdRequest> = emptyList()) = UtkastRequest(
            innhold = innhold,
            bakgrunnsinformasjon = "Bakgrunnen for...",
            deltakelsesprosent = null,
            dagerPerUke = null,
        )

        private fun pameldingUtenGodkjenningRequest(innhold: List<InnholdRequest> = emptyList()) = PameldingUtenGodkjenningRequest(
            innhold = innhold,
            bakgrunnsinformasjon = "Bakgrunnen for...",
            deltakelsesprosent = null,
            dagerPerUke = null,
        )

        private fun List<Innhold>.toInnholdDto() = this.map {
            InnholdRequest(
                it.innholdskode,
                it.beskrivelse,
            )
        }
    }
}
