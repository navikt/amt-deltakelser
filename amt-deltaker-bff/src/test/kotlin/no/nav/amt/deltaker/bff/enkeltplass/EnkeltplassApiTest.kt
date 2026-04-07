package no.nav.amt.deltaker.bff.enkeltplass

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerResponse
import no.nav.amt.deltaker.bff.veileder.api.request.OpprettEnkeltplassKladdRequest
import no.nav.amt.deltaker.bff.veileder.api.utils.createPostRequest
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.PersonIdentResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.internapi.paamelding.request.OppdaterEnkeltplassKladdRequest
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class EnkeltplassApiTest : IntegrationTestBase() {
    override val tilgangskontrollService: TilgangskontrollService = mockk(relaxed = true)

    private val deltakerInTest = lagDeltaker()

    @BeforeEach
    fun setup() {
        coEvery { amtDeltakerClient.getPersonidentForDeltaker(deltakerInTest.id) } returns PersonIdentResponse(PERSONIDENT_IN_TEST)
        every { tilgangskontrollService.verifiserSkrivetilgang(any<UUID>(), any<String>()) } just runs

        val mockHttpResponse = mockk<HttpResponse>()
        coEvery { enkeltplassClient.meldPaaDirekte(deltakerInTest.id, any()) } returns mockHttpResponse
        coEvery { enkeltplassClient.opprettKladdEnkeltplass(any(), any()) } returns DeltakerIdResponse(deltakerInTest.id)
        coEvery { enkeltplassClient.oppdaterKladdEnkeltplass(any(), any()) } returns mockHttpResponse
        coEvery { amtDeltakerClient.getDeltaker(deltakerInTest.id) } returns lagDeltakerResponse()
    }

    @Nested
    inner class KladdTests {
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
                prisinformasjon = null,
                beskrivelse = null,
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
    inner class EnkeltplassUtkastTests {
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
                    createPostRequest(requestInTest)
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
                    createPostRequest(requestInTest.copy(arrangorOrgnummer = "abc"))
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
                    createPostRequest(requestInTest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK
        }
    }

    companion object {
        private const val PERSONIDENT_IN_TEST = "1234"
        private val requestInTest = EnkeltplassPameldingRequest(
            beskrivelse = "Testbeskrivelse",
            prisinformasjon = "Test prisinformasjon",
            arrangorOrgnummer = "987654321",
        )
    }
}
