package no.nav.amt.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.amt.deltaker.deltaker.OpprettKladdRequestValidator
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.internapi.paamelding.request.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.paamelding.request.OpprettKladdEnkeltplassRequest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class EnkeltplassApiTest : IntegrationTestBase() {
    override val enkeltplassService = mockk<EnkeltplassService>(relaxed = true)
    override val opprettKladdRequestValidator = mockk<OpprettKladdRequestValidator>(relaxed = true)

    @Nested
    inner class MeldPaaDirekteTests {
        @Test
        fun `mangler token - returnerer Unauthorized`() {
            withTestApplicationContext { client ->
                client
                    .post("/enkeltplass/utkast/${UUID.randomUUID()}/meld-paa-direkte")
                    .status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `skal returnere 200 OK`() {
            val deltakerId = UUID.randomUUID()

            val request = EnkeltplassPameldingRequest(
                beskrivelse = "Testbeskrivelse",
                prisinformasjon = "Test prisinformasjon",
                arrangorOrgnummer = "987654321",
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now(),
            )

            val decoratedRequest = EnkeltplassPameldingDecoratedRequest(
                wrappedRequest = request,
                endretAvEnhet = "1234",
                endretAv = "123456789",
            )

            withTestApplicationContext { client ->
                client
                    .post("/enkeltplass/utkast/$deltakerId/meld-paa-direkte") {
                        postRequest(decoratedRequest)
                    }.status shouldBe HttpStatusCode.OK
            }

            coVerify { enkeltplassService.meldPaaDirekte(deltakerId, decoratedRequest) }
        }
    }

    @Nested
    inner class Enkeltplass {
        private val opprettEnkeltplassKladdRequest = OpprettKladdEnkeltplassRequest(Tiltakskode.ARBEIDSMARKEDSOPPLAERING, "1234")
        private val oppdaterEnkeltplassKladdRequest =
            OppdaterEnkeltplassKladdRequest(LocalDate.now(), LocalDate.now(), "prisinfo", "beskrivelse")

        @Test
        fun `post kladd - mangler token - returnerer 401`() {
            withTestApplicationContext { client ->
                client.post("/enkeltplass/opprett-kladd") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `opprett enkeltplass kladd - har tilgang - returnerer deltakerId`() {
            val deltaker = TestData.lagDeltaker()

            coEvery { opprettKladdRequestValidator.validateRequest(any()) } returns ValidationResult.Valid
            coEvery {
                enkeltplassService.opprettKladd(
                    tiltakskode = any<Tiltakskode>(),
                    personident = any(),
                )
            } returns deltaker

            withTestApplicationContext { client ->
                val response = client.post("/enkeltplass/opprett-kladd") {
                    postRequest(opprettEnkeltplassKladdRequest)
                }

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe objectMapper.writeValueAsString(
                    DeltakerIdResponse(
                        deltaker.id,
                    ),
                )
            }
        }

        @Test
        fun `oppdater enkeltplass kladd - har tilgang - returnerer deltakerId`() {
            val deltaker = TestData.lagDeltaker()

            coEvery { opprettKladdRequestValidator.validateRequest(any()) } returns ValidationResult.Valid
            coEvery {
                enkeltplassService.oppdaterKladd(
                    deltakerId = deltaker.id,
                    startdato = any(),
                    sluttdato = any(),
                    beskrivelse = any(),
                    prisinformasjon = any(),
                )
            } returns deltaker

            withTestApplicationContext { client ->
                val response = client.post("/enkeltplass/oppdater-kladd/${deltaker.id}") {
                    postRequest(oppdaterEnkeltplassKladdRequest)
                }

                response.status shouldBe HttpStatusCode.OK
            }
        }

        @Test
        fun `oppdater enkeltplass kladd - mangler token - returnerer 401`() {
            withTestApplicationContext { client ->
                client
                    .post("/enkeltplass/oppdater-kladd/${UUID.randomUUID()}") { setBody("foo") }
                    .status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }
}
