package no.nav.amt.deltaker.api

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.application.plugins.OpprettKladdRequestValidator
import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.veileder.lagInnsok
import no.nav.amt.internapi.DeltakerIdResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.enkeltplass.OpprettKladdEnkeltplassRequest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class EnkeltplassApiTest : IntegrationTestBase() {
    override val enkeltplassService = mockk<EnkeltplassService>()
    override val opprettKladdRequestValidator = mockk<OpprettKladdRequestValidator>()

    @Nested
    inner class OpprettKladdTests {
        private val opprettEnkeltplassKladdRequest = OpprettKladdEnkeltplassRequest(Tiltakskode.ARBEIDSMARKEDSOPPLAERING, "1234")

        @Test
        fun `opprett enkeltplasskladd - mangler token - returnerer Unauthorized`() {
            withTestApplicationContext { client ->
                client.post("/enkeltplass/opprett-kladd") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `opprett enkeltplasskladd - har tilgang - returnerer deltakerId`() {
            val deltaker = lagDeltaker()

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
    }

    @Nested
    inner class OppdaterKladdTests {
        private val oppdaterEnkeltplassKladdRequest = OppdaterEnkeltplassKladdRequest(
            beskrivelse = "beskrivelse",
            prisinformasjon = "prisinfo",
            arrangorUnderenhet = "987654322",
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusDays(1),
        )

        @Test
        fun `oppdater enkeltplasskladd - mangler token - returnerer Unauthorized`() {
            withTestApplicationContext { client ->
                client
                    .post("/enkeltplass/oppdater-kladd/${UUID.randomUUID()}") { setBody("foo") }
                    .status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `oppdater enkeltplasskladd - har tilgang - returnerer OK`() = runTest {
            // Arrange
            val deltakerInTest = lagDeltaker()

            coEvery { opprettKladdRequestValidator.validateRequest(any()) } returns ValidationResult.Valid
            coEvery {
                enkeltplassService.oppdaterKladd(
                    deltakerId = deltakerInTest.id,
                    oppdaterKladdRequest = any(),
                )
            } just Runs

            // Act
            val response = withTestApplicationContext { client ->
                client.post("/enkeltplass/oppdater-kladd/${deltakerInTest.id}") {
                    postRequest(oppdaterEnkeltplassKladdRequest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK
        }
    }

    @Nested
    inner class UtkastTests {
        private val pameldingRequest = EnkeltplassPameldingRequest(
            beskrivelse = "beskrivelse",
            prisinformasjon = "prisinfo",
            arrangorUnderenhet = "987654322",
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusDays(1),
        )

        private val decoratedRequest = EnkeltplassPameldingDecoratedRequest(
            endretAv = "123456789",
            endretAvEnhet = "1234",
            wrappedRequest = pameldingRequest,
        )

        @Test
        fun `oppdater utkast - mangler token - returnerer Unauthorized`() {
            withTestApplicationContext { client ->
                client
                    .post("/enkeltplass/utkast/${UUID.randomUUID()}") { setBody("foo") }
                    .status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `oppdater utkast - har tilgang - returnerer deltaker`() = runTest {
            // Arrange
            val tempDeltaker = lagDeltaker()

            val vedtakInTest = lagVedtak(
                deltakerVedVedtak = tempDeltaker,
                opprettetAv = lagNavAnsatt(id = tempDeltaker.navBruker.navVeilederId.shouldNotBeNull()),
                opprettetAvEnhet = lagNavEnhet(id = tempDeltaker.navBruker.navEnhetId.shouldNotBeNull()),
                sistEndretAv = lagNavAnsatt(id = tempDeltaker.navBruker.navVeilederId.shouldNotBeNull()),
                sistEndretAvEnhet = lagNavEnhet(id = tempDeltaker.navBruker.navEnhetId.shouldNotBeNull()),
            )

            val deltakerInTest = tempDeltaker.copy(
                vedtaksinformasjon = vedtakInTest.tilVedtaksInformasjon(),
            )

            coEvery { opprettKladdRequestValidator.validateRequest(any()) } returns ValidationResult.Valid
            coEvery {
                enkeltplassService.oppdaterUtkast(
                    deltakerId = deltakerInTest.id,
                    decoratedRequest = any(),
                )
            } returns deltakerInTest

            // responseBuilder mocks
            coEvery {
                navAnsattRepository.getManyById(setOf(deltakerInTest.navBruker.navVeilederId.shouldNotBeNull()))
            } returns listOf(lagNavAnsatt(id = deltakerInTest.navBruker.navVeilederId.shouldNotBeNull()))
            coEvery {
                navEnhetRepository.getMany(setOf(deltakerInTest.navBruker.navEnhetId.shouldNotBeNull()))
            } returns listOf(lagNavEnhet(id = deltakerInTest.navBruker.navEnhetId.shouldNotBeNull()))

            every {
                innsokPaaFellesOppstartRepository.getForDeltaker(deltakerInTest.id)
            } returns Result.success(TestData.lagInnsok(deltakerInTest))

            every {
                deltakerRepository.getDeltakelserForLaaseSjekk(
                    deltakerInTest.navBruker.personident,
                    deltakerInTest.deltakerliste.id,
                )
            } returns listOf(
                no.nav.amt.deltaker.repository.DeltakelseLaaseInfo(
                    id = deltakerInTest.id,
                    statusType = deltakerInTest.status.type,
                    statusGyldigFra = deltakerInTest.status.gyldigFra,
                    vedtakFattet = null,
                    innsoektDatoFraArena = null,
                ),
            )

            coEvery { forslagRepository.getForDeltaker(deltakerInTest.id) } returns emptyList()
            coEvery { distribusjonClient.digitalBruker(any()) } returns true
            every { deltakerEndringRepository.getForDeltaker(deltakerInTest.id) } returns emptyList()
            every { vedtakRepository.getForDeltaker(deltakerInTest.id) } returns null
            every { vurderingRepository.getForDeltaker(deltakerInTest.id) } returns emptyList()
            every { endringFraArrangorRepository.getForDeltaker(deltakerInTest.id) } returns emptyList()
            every { importertFraArenaRepository.getForDeltaker(deltakerInTest.id) } returns null
            every { endringFraTiltakskoordinatorRepository.getForDeltaker(deltakerInTest.id) } returns emptyList()
            every { deltakerRepository.getSoktInnDato(any()) } returns null

            // Act
            val response = withTestApplicationContext { client ->
                client.post("/enkeltplass/utkast/${deltakerInTest.id}") {
                    postRequest(decoratedRequest)
                }
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK
            response.body<DeltakerResponse>().id shouldBe deltakerInTest.id
        }
    }

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
        fun `skal melde pa deltaker direkte`() = runTest {
            // Arrange
            val deltakerInTest = lagDeltaker()

            val request = EnkeltplassPameldingRequest(
                beskrivelse = "Testbeskrivelse",
                prisinformasjon = "Test prisinformasjon",
                arrangorUnderenhet = "987654322",
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now(),
            )

            val decoratedRequest = EnkeltplassPameldingDecoratedRequest(
                wrappedRequest = request,
                endretAvEnhet = "1234",
                endretAv = "123456789",
            )

            coEvery {
                enkeltplassService.meldPaaDirekte(
                    deltakerId = deltakerInTest.id,
                    decoratedRequest = decoratedRequest,
                )
            } just Runs

            // Act
            val response = withTestApplicationContext { client ->
                client
                    .post("/enkeltplass/utkast/${deltakerInTest.id}/meld-paa-direkte") {
                        postRequest(decoratedRequest)
                    }
            }

            // Assert
            response.status shouldBe HttpStatusCode.OK

            coVerify {
                enkeltplassService.meldPaaDirekte(
                    deltakerId = deltakerInTest.id,
                    decoratedRequest = decoratedRequest,
                )
            }
        }
    }
}
