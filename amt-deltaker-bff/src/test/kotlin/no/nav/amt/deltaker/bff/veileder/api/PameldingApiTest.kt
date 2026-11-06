package no.nav.amt.deltaker.bff.veileder.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerOld
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakstype
import no.nav.amt.deltaker.bff.veileder.api.request.PameldingUtenGodkjenningRequest
import no.nav.amt.deltaker.bff.veileder.api.request.UtkastRequest
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.noBodyRequest
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID

class PameldingApiTest : IntegrationTestBase() {
    @ParameterizedTest
    @MethodSource("tiltakMedBakgrunnsinfo")
    fun `tiltak med bakgrunnsinfo krever utfylt tekst - returnerer 400 uten å sende utkast`(tiltakskode: Tiltakskode) {
        val deltaker = lagDeltakerOld(
            status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            tiltakstype = lagTiltakstype(tiltakskode = tiltakskode),
        )
        val response = lagDeltakerResponse(deltaker)
        coEvery { amtDeltakerClient.getDeltaker(deltaker.id) } returns response
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        coEvery { paameldingClient.utkast(any(), any()) } returns response

        withTestApplicationContext { httpClient ->
            listOf(null, "", "   ", "\t\n").forEach { bakgrunnsinformasjon ->
                val request = utkastRequest(deltaker.deltakelsesinnhold!!.innhold.toInnholdDto())
                    .copy(bakgrunnsinformasjon = bakgrunnsinformasjon)
                listOf("", "/utenGodkjenning").forEach { handling ->
                    httpClient
                        .post("/pamelding/${deltaker.id}$handling") {
                            createPostRequest(request)
                        }.apply {
                            status shouldBe HttpStatusCode.BadRequest
                            bodyAsText() shouldBe "400: Bakgrunnsinfo må fylles ut."
                        }
                }
            }
        }

        coVerify(exactly = 0) { paameldingClient.utkast(any(), any()) }
    }

    @ParameterizedTest
    @MethodSource("tiltakUtenBakgrunnsinfo")
    fun `påmelding uten bakgrunnsinfo tillates for tiltak uten feltet`(tiltakskode: Tiltakskode) {
        val deltaker = lagDeltakerOld(
            status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            tiltakstype = lagTiltakstype(tiltakskode = tiltakskode, innhold = null),
        )
        val response = lagDeltakerResponse(deltaker)
        coEvery { amtDeltakerClient.getDeltaker(deltaker.id) } returns response
        coEvery { amtDistribusjonClient.digitalBruker(any()) } returns true
        coEvery { paameldingClient.utkast(any(), any()) } returns response

        withTestApplicationContext { httpClient ->
            listOf(null, "", "   ", "\t\n").forEach { bakgrunnsinformasjon ->
                listOf("", "/utenGodkjenning").forEach { handling ->
                    httpClient
                        .post("/pamelding/${deltaker.id}$handling") {
                            createPostRequest(utkastRequest().copy(bakgrunnsinformasjon = bakgrunnsinformasjon))
                        }.status shouldBe HttpStatusCode.OK
                }
            }
        }

        coVerify(exactly = 8) { paameldingClient.utkast(deltaker.id, any()) }
    }

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
        val deltaker = lagDeltakerOld(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))
        val amtDeltakerResponse = lagDeltakerResponse(deltaker)
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        coEvery { amtDeltakerClient.getDeltaker(deltaker.id) } returns amtDeltakerResponse
        coEvery { paameldingClient.utkast(any(), any()) } returns amtDeltakerResponse
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
        coEvery { paameldingClient.utkast(any(), any()) } throws NoSuchElementException()
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
        val deltaker = lagDeltakerOld(
            status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
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

    companion object {
        @JvmStatic
        fun tiltakMedBakgrunnsinfo() = listOf(
            Tiltakskode.ARBEIDSRETTET_REHABILITERING,
            Tiltakskode.AVKLARING,
            Tiltakskode.OPPFOLGING,
            Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER,
        )

        @JvmStatic
        fun tiltakUtenBakgrunnsinfo() = listOf(
            Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
            Tiltakskode.HOYERE_UTDANNING,
            Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
            Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            Tiltakskode.STUDIESPESIALISERING,
            Tiltakskode.FAG_OG_YRKESOPPLAERING,
            Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
            Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
            Tiltakskode.JOBBKLUBB,
        )

        private fun List<Innhold>.toInnholdDto() = this.map {
            InnholdsElementRequest(
                it.innholdskode,
                it.beskrivelse,
            )
        }
    }
}
