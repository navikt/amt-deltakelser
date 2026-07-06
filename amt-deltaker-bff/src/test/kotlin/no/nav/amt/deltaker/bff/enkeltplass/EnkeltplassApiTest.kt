package no.nav.amt.deltaker.bff.enkeltplass

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerOld
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.request.OpprettEnkeltplassKladdRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.ktor.clients.kodeverk.SertifiseringResponse
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class EnkeltplassApiTest : IntegrationTestBase() {
    override val tilgangskontrollService: TilgangskontrollService = mockk(relaxed = true)

    private val deltakerInTest = lagDeltakerOld()

    @BeforeEach
    fun setup() {
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(deltakerInTest.id) } returns
            PersonIdentResponse(PERSONIDENT_IN_TEST).personident
        every { tilgangskontrollService.verifiserSkrivetilgang(any<UUID>(), any<String>()) } just runs

        val mockHttpResponse = mockk<HttpResponse>()
        coEvery { enkeltplassClient.opprettKladd(any(), any()) } returns DeltakerIdResponse(deltakerInTest.id)
        coEvery { enkeltplassClient.oppdaterKladd(any(), any()) } returns mockHttpResponse
        coEvery { enkeltplassClient.oppdaterUtkast(any(), any()) } returns lagDeltakerResponse()
        coEvery { enkeltplassClient.meldPaaDirekte(deltakerInTest.id, any()) } returns mockHttpResponse
        coEvery { amtDeltakerClient.getDeltaker(deltakerInTest.id) } returns lagDeltakerResponse()
    }

    @Nested
    inner class SertifiseringSokTests {
        @Test
        fun `skal returnere Unauthorized nar tilgang mangler`() {
            // Act
            val response = withTestApplicationContext { client ->
                client.get("/enkeltplass/kodeverk-sertifiseringer/sok/foo")
            }

            // Assert
            response.status shouldBe HttpStatusCode.Unauthorized
        }

        @Test
        fun `skal returnere sokeresultat`() = runTest {
            // Arrange
            val expectedResponse = listOf(
                SertifiseringResponse(konseptId = 1, label = "Sertifisering 1"),
            )

            coEvery { opplaringKategoriseringClient.sertifiseringSok(any()) } returns expectedResponse

            // Act
            val response = withTestApplicationContext { client ->
                client.get("/enkeltplass/kodeverk-sertifiseringer/sok/foo") {
                    bearerAuth(bearerTokenInTest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK
            response.body<List<SertifiseringResponse>>() shouldBe expectedResponse
        }
    }

    @Nested
    inner class KodeverkForDeltakerTests {
        @Test
        fun `skal returnere Unauthorized nar tilgang mangler`() {
            val response = withTestApplicationContext { client ->
                client.get("/enkeltplass/kodeverk/${deltakerInTest.id}")
            }

            response.status shouldBe HttpStatusCode.Unauthorized
        }

        @Test
        fun `skal returnere Forbidden nar veileder ikke har lesetilgang til bruker`() {
            every { tilgangskontrollService.verifiserLesetilgang(any(), any()) } throws AuthorizationException("")

            val response = withTestApplicationContext { client ->
                client.get("/enkeltplass/kodeverk/${deltakerInTest.id}") {
                    bearerAuth(bearerTokenInTest)
                }
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }

        @Test
        fun `skal returnere kodeverk med valgte verdier`() = runTest {
            val verdiId = UUID.randomUUID()
            val deltakerResponse = lagDeltakerResponse(id = deltakerInTest.id).let {
                it.copy(
                    gjennomforing = it.gjennomforing.copy(
                        opplaringKategoriseringValg = OpplaringKategoriseringValg(
                            valgteKategoriseringer = setOf(
                                OpplaringKategoriseringValg.ValgteFelt(
                                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                                    valg = mapOf(verdiId to "Bygg"),
                                ),
                            ),
                            valgteSertifiseringer = emptySet(),
                        ),
                    ),
                )
            }
            val tiltakskode = deltakerResponse.gjennomforing.tiltakstype.tiltakskode

            val kodeverkFraClient = OpplaringKategoriseringResponse(
                tiltakskode = tiltakskode,
                alternativer = listOf(
                    OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                        id = UUID.randomUUID(),
                        pakrevd = true,
                        visningsnavn = "Bransje",
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                        alternativer = listOf(
                            OpplaringKategoriseringResponse.Alternativ.Verdi(
                                id = verdiId,
                                visningsnavn = "Bygg",
                                valgt = false,
                            ),
                        ),
                    ),
                ),
            )

            coEvery { amtDeltakerClient.getDeltaker(deltakerInTest.id) } returns deltakerResponse
            coEvery { opplaringKategoriseringClient.hentOpplaringKategorisering(tiltakskode) } returns kodeverkFraClient

            val response = withTestApplicationContext { client ->
                client.get("/enkeltplass/kodeverk/${deltakerInTest.id}") {
                    bearerAuth(bearerTokenInTest)
                }
            }

            response.status shouldBe HttpStatusCode.OK
            response.body<OpplaringKategoriseringResponse>() shouldBe kodeverkFraClient.settValg(
                deltakerResponse.gjennomforing.opplaringKategoriseringValg,
            )

            coVerify(exactly = 1) { amtDeltakerClient.getDeltaker(deltakerInTest.id) }
            coVerify(exactly = 1) { opplaringKategoriseringClient.hentOpplaringKategorisering(tiltakskode) }
            verify(exactly = 1) { tilgangskontrollService.verifiserLesetilgang(any(), any()) }
        }
    }

    @Nested
    inner class OpprettKladdTests {
        @Nested
        inner class OpprettKladdTests {
            private val requestInTest = OpprettEnkeltplassKladdRequest(
                tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                personident = PERSONIDENT_IN_TEST,
            )
            val url = "/enkeltplass/opprett-kladd"

            @Test
            fun `skal returnere Unauthorized nar tilgang mangler`() {
                // Act
                val response = withTestApplicationContext { client ->
                    client.post(url)
                }

                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }

            @Test
            fun `skal returnere Forbidden nar veileder ikke har tilgang til bruker`() {
                // Arrange
                every { tilgangskontrollService.verifiserSkrivetilgang(any(), any()) } throws AuthorizationException("")

                // Act
                val response = withTestApplicationContext { client ->
                    client.post(url) {
                        createPostRequest(requestInTest)
                    }
                }

                // Assert
                response.status shouldBe HttpStatusCode.Forbidden
            }

            @Test
            fun `skal returnere OK nar kladd er opprettet`() = runTest {
                // Arrange
                coEvery { opplaringKategoriseringClient.hentOpplaringKategorisering(any()) } returns OpplaringKategoriseringResponse(
                    tiltakskode = requestInTest.tiltakskode,
                    alternativer = emptyList(),
                )

                // Act
                val response = withTestApplicationContext { client ->
                    client.post(url) {
                        createPostRequest(requestInTest)
                    }
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
            }
        }

        @Nested
        inner class OppdaterKladdTests {
            val url = "/enkeltplass/oppdater-kladd/${deltakerInTest.id}"
            private val requestInTest = OppdaterEnkeltplassKladdRequest(
                startdato = null,
                sluttdato = null,
                beskrivelse = null,
                arrangorUnderenhet = null,
            )

            @Test
            fun `skal returnere Unauthorized nar tilgang mangler`() {
                // Act
                val response = withTestApplicationContext { client ->
                    client.post(url)
                }

                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }

            @Test
            fun `skal returnere Forbidden nar veileder ikke har tilgang til bruker`() {
                // Arrange
                every { tilgangskontrollService.verifiserSkrivetilgang(any(), any()) } throws AuthorizationException("")

                // Act
                val response = withTestApplicationContext { client ->
                    client.post(url) {
                        createPostRequest(requestInTest)
                    }
                }

                // Assert
                response.status shouldBe HttpStatusCode.Forbidden
            }

            @Test
            fun `skal returnere OK nar kladd er oppdatert`() = runTest {
                // Act
                val response = withTestApplicationContext { client ->
                    client.post(url) {
                        createPostRequest(requestInTest)
                    }
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Nested
    inner class UtkastTests {
        val utkastUrlInTest = "/enkeltplass/utkast/${deltakerInTest.id}"

        @Test
        fun `skal returnere Unauthorized nar tilgang mangler`() {
            // Act
            val response = withTestApplicationContext { client ->
                client.post(utkastUrlInTest)
            }

            // Assert
            response.status shouldBe HttpStatusCode.Unauthorized
        }

        @Test
        fun `skal returnere Forbidden nar veileder ikke har tilgang til bruker`() {
            // Arrange
            every { tilgangskontrollService.verifiserSkrivetilgang(any(), any()) } throws AuthorizationException("")

            // Act
            val response = withTestApplicationContext { client ->
                client.post(utkastUrlInTest) {
                    createPostRequest(enkeltplassPameldingRequest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.Forbidden
        }

        @Test
        fun `skal returnere BadRequest hvis request er ugyldig`() {
            // Act
            val response = withTestApplicationContext { client ->
                client.post(utkastUrlInTest) {
                    createPostRequest(enkeltplassPameldingRequest.copy(arrangorUnderenhet = "abc"))
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.BadRequest
        }

        @Test
        fun `skal returnere OK nar utkast er oppdatert`() = runTest {
            // Arrange
            coEvery { opplaringKategoriseringClient.hentOpplaringKategorisering(any()) } returns OpplaringKategoriseringResponse(
                tiltakskode = deltakerInTest.deltakerliste.tiltak.tiltakskode,
                alternativer = emptyList(),
            )

            // Act
            val response = withTestApplicationContext { client ->
                client.post(utkastUrlInTest) {
                    createPostRequest(enkeltplassPameldingRequest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK
        }
    }

    @Nested
    inner class MeldPaaDirekteTests {
        val url = "/enkeltplass/utkast/${deltakerInTest.id}/meld-paa-direkte"

        @Test
        fun `skal returnere Unauthorized nar tilgang mangler`() {
            // Act
            val response = withTestApplicationContext { client ->
                client.post(url)
            }

            // Assert
            response.status shouldBe HttpStatusCode.Unauthorized
        }

        @Test
        fun `skal returnere Forbidden nar veileder ikke har tilgang til bruker`() {
            // Arrange
            every { tilgangskontrollService.verifiserSkrivetilgang(any(), any()) } throws AuthorizationException("")

            // Act
            val response = withTestApplicationContext { client ->
                client.post(url) {
                    createPostRequest(enkeltplassPameldingRequest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.Forbidden
        }

        @Test
        fun `skal returnere BadRequest hvis request er ugyldig`() {
            // Act
            val response = withTestApplicationContext { client ->
                client.post(url) {
                    createPostRequest(
                        enkeltplassPameldingRequest
                            .copy(arrangorUnderenhet = "abc"),
                    )
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.BadRequest
        }

        @Test
        fun `skal returnere OK nar enkeltplass er opprettet`() = runTest {
            // Act
            val response = withTestApplicationContext { client ->
                client.post(url) {
                    createPostRequest(enkeltplassPameldingRequest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK
        }
    }

    companion object {
        private const val PERSONIDENT_IN_TEST = "1234"
        private val enkeltplassPameldingRequest = EnkeltplassPameldingRequest(
            beskrivelse = "Testbeskrivelse",
            arrangorUnderenhet = "987654322",
            prisinformasjon = PrisinformasjonDto.Anskaffelse(pris = 1000000),
        )
    }
}
