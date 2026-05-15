package no.nav.amt.deltaker.enkeltplass

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.SertifiseringValgRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.database.Database.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class EnkeltplassServiceTest : IntegrationTestBase() {
    override val deltakerService = mockk<DeltakerService>()
    override val vedtakService = mockk<VedtakService>()
    override val navEnhetService = mockk<NavEnhetService>()
    override val navAnsattService = mockk<NavAnsattService>()

    @BeforeEach
    fun setup() {
        every { deltakerRepository.get(deltakerInTest.id) } returns Result.success(deltakerInTest)
        coEvery { navEnhetService.hentEllerOpprettNavEnhet(navEnhetInTest.enhetsnummer) } returns navEnhetInTest
        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(navAnsattInTest.navIdent) } returns navAnsattInTest

        mockkObject(Database)
        every { transaction<Any>(any()) } answers {
            val block = firstArg<() -> Any>()
            block()
        }

        mockkObject(SertifiseringValgRepository)
        every { SertifiseringValgRepository.deleteForGjennomforing(any()) } just Runs
        every { SertifiseringValgRepository.lagreSertifiseringValg(any(), any()) } just Runs
    }

    @AfterEach
    fun cleanup() {
        unmockkObject(Database)
        unmockkObject(SertifiseringValgRepository)
    }

    @Nested
    inner class OppdaterKladdTests {
        private val oppdaterKladdRequest = OppdaterEnkeltplassKladdRequest(
            beskrivelse = null,
            prisinformasjon = null,
            startdato = null,
            sluttdato = null,
            arrangorUnderenhet = null,
        )

        @Test
        fun `skal kaste exception for deltaker som ikke er enkeltplass`() = runTest {
            val deltaker = deltakerInTest.copy(
                deltakerliste = deltakerInTest.deltakerliste.copy(gjennomforingstype = GjennomforingType.Gruppe),
            )
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            shouldThrow<IllegalArgumentException> {
                enkeltplassService.oppdaterKladd(
                    deltakerId = deltaker.id,
                    oppdaterKladdRequest = oppdaterKladdRequest,
                )
            }
        }

        @Test
        fun `skal kaste exception for deltaker som ikke er i KLADD status`() = runTest {
            val deltaker = deltakerInTest.copy(
                status = deltakerInTest.status.copy(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            shouldThrow<IllegalArgumentException> {
                enkeltplassService.oppdaterKladd(
                    deltakerId = deltaker.id,
                    oppdaterKladdRequest = oppdaterKladdRequest,
                )
            }
        }

        @Test
        fun `sertifiseringValg med verdier - sletter eksisterende og lagrer nye`() = runTest {
            // Arrange
            every { deltakerlisteRepository.update(any()) } just Runs
            every { deltakerRepository.updateEnkeltplassKladd(any()) } just Runs

            val sertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
                SertifiseringValg(id = 2, navn = "Truckfører T2"),
            )
            val request = oppdaterKladdRequest.copy(sertifiseringValg = sertifiseringer)

            // Act
            enkeltplassService.oppdaterKladd(
                deltakerId = deltakerInTest.id,
                oppdaterKladdRequest = request,
            )

            // Assert
            verify { SertifiseringValgRepository.deleteForGjennomforing(deltakerInTest.deltakerliste.id) }
            verify { SertifiseringValgRepository.lagreSertifiseringValg(deltakerInTest.deltakerliste.id, sertifiseringer) }
        }

        @Test
        fun `sertifiseringValg tomt sett - sletter eksisterende uten aa lagre nye`() = runTest {
            // Arrange
            every { deltakerlisteRepository.update(any()) } just Runs
            every { deltakerRepository.updateEnkeltplassKladd(any()) } just Runs

            val request = oppdaterKladdRequest.copy(sertifiseringValg = emptySet())

            // Act
            enkeltplassService.oppdaterKladd(
                deltakerId = deltakerInTest.id,
                oppdaterKladdRequest = request,
            )

            // Assert
            verify { SertifiseringValgRepository.deleteForGjennomforing(deltakerInTest.deltakerliste.id) }
            verify(exactly = 0) { SertifiseringValgRepository.lagreSertifiseringValg(any(), any()) }
        }

        @Test
        fun `sertifiseringValg null - rører ikke sertifiseringer`() = runTest {
            // Arrange
            every { deltakerlisteRepository.update(any()) } just Runs
            every { deltakerRepository.updateEnkeltplassKladd(any()) } just Runs

            val request = oppdaterKladdRequest.copy(sertifiseringValg = null)

            // Act
            enkeltplassService.oppdaterKladd(
                deltakerId = deltakerInTest.id,
                oppdaterKladdRequest = request,
            )

            // Assert
            verify(exactly = 0) { SertifiseringValgRepository.deleteForGjennomforing(any()) }
            verify(exactly = 0) { SertifiseringValgRepository.lagreSertifiseringValg(any(), any()) }
        }
    }

    @Nested
    inner class DelUtkastMedInnbyggerTests {
        @Test
        fun `skal oppdatere deltaker, sette status UTKAST_TIL_PAMELDING og opprette vedtak`() = runTest {
            every {
                deltakerService.lagreDeltakerStatus(
                    deltakerId = deltakerInTest.id,
                    nyDeltakerStatus = match { it.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING },
                    erDeltakerSluttdatoEndret = any(),
                )
            } returns deltakerInTest.status

            every {
                vedtakService.opprettEllerOppdaterVedtak(
                    fattetAvNav = false,
                    endretAv = navAnsattInTest,
                    endretAvEnhet = navEnhetInTest,
                    deltaker = any(),
                    fattetDato = null,
                )
            } returns lagVedtak(
                deltakerId = deltakerInTest.id,
                deltakerVedVedtak = deltakerInTest,
            )

            every { arrangorRepository.get(any<String>()) } returns arrangorInTest
            every { deltakerRepository.updateEnkeltplassKladd(any()) } just Runs
            every { deltakerlisteRepository.update(any()) } just Runs

            enkeltplassService.delUtkastMedInnbygger(
                deltakerId = deltakerInTest.id,
                decoratedRequest = decoratedRequest,
            )

            verify {
                deltakerService.lagreDeltakerStatus(
                    deltakerId = deltakerInTest.id,
                    nyDeltakerStatus = match { it.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING },
                    erDeltakerSluttdatoEndret = any(),
                )
            }
            verify {
                vedtakService.opprettEllerOppdaterVedtak(
                    fattetAvNav = false,
                    endretAv = navAnsattInTest,
                    endretAvEnhet = navEnhetInTest,
                    deltaker = any(),
                    fattetDato = null,
                )
            }
            verify { deltakerlisteRepository.update(any()) }
            verify { deltakerRepository.updateEnkeltplassKladd(any()) }
        }

        @Test
        fun `skal kaste exception for gjennomforing som ikke er enkeltplass`() = runTest {
            val deltaker = deltakerInTest.copy(
                deltakerliste = deltakerInTest.deltakerliste.copy(
                    gjennomforingstype = GjennomforingType.Gruppe,
                    status = GjennomforingStatusType.KLADD,
                ),
            )
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            shouldThrow<IllegalArgumentException> {
                enkeltplassService.delUtkastMedInnbygger(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }
        }

        @Test
        fun `skal kaste exception for gjennomforing som ikke er i KLADD status`() = runTest {
            val deltaker = deltakerInTest.copy(
                deltakerliste = deltakerInTest.deltakerliste.copy(status = GjennomforingStatusType.GJENNOMFORES),
            )
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            shouldThrow<IllegalArgumentException> {
                enkeltplassService.delUtkastMedInnbygger(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }
        }
    }

    @Nested
    inner class OppdaterUtkastTests {
        @Test
        fun `skal oppdatere deltaker og vedtak uten aa endre status`() = runTest {
            every {
                vedtakService.opprettEllerOppdaterVedtak(
                    fattetAvNav = false,
                    endretAv = navAnsattInTest,
                    endretAvEnhet = navEnhetInTest,
                    deltaker = any(),
                    fattetDato = null,
                )
            } returns lagVedtak(
                deltakerId = deltakerInTest.id,
                deltakerVedVedtak = deltakerInTest,
            )

            every { arrangorRepository.get(any<String>()) } returns arrangorInTest
            every { deltakerRepository.updateEnkeltplassKladd(any()) } just Runs
            every { deltakerlisteRepository.update(any()) } just Runs

            enkeltplassService.oppdaterUtkast(
                deltakerId = deltakerInTest.id,
                decoratedRequest = decoratedRequest,
            )

            verify(exactly = 0) {
                deltakerService.lagreDeltakerStatus(any(), any(), any())
            }
            verify {
                vedtakService.opprettEllerOppdaterVedtak(
                    fattetAvNav = false,
                    endretAv = navAnsattInTest,
                    endretAvEnhet = navEnhetInTest,
                    deltaker = any(),
                    fattetDato = null,
                )
            }
            verify { deltakerlisteRepository.update(any()) }
            verify { deltakerRepository.updateEnkeltplassKladd(any()) }
        }

        @Test
        fun `skal kaste exception for gjennomforing som ikke er enkeltplass`() = runTest {
            val deltaker = deltakerInTest.copy(
                deltakerliste = deltakerInTest.deltakerliste.copy(
                    gjennomforingstype = GjennomforingType.Gruppe,
                    status = GjennomforingStatusType.KLADD,
                ),
            )
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            shouldThrow<IllegalArgumentException> {
                enkeltplassService.oppdaterUtkast(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }
        }

        @Test
        fun `skal kaste exception for gjennomforing som ikke er i KLADD status`() = runTest {
            val deltaker = deltakerInTest.copy(
                deltakerliste = deltakerInTest.deltakerliste.copy(status = GjennomforingStatusType.GJENNOMFORES),
            )
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            shouldThrow<IllegalArgumentException> {
                enkeltplassService.oppdaterUtkast(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }
        }
    }

    @Nested
    inner class MeldPaaDirekteTests {
        @Test
        fun `skal sette status SOKT_INN, fatte vedtak og publisere OpprettEnkeltplass`() = runTest {
            // Arrange
            every {
                deltakerService.lagreDeltakerStatus(
                    deltakerId = deltakerInTest.id,
                    nyDeltakerStatus = match { it.type == DeltakerStatus.Type.SOKT_INN },
                    erDeltakerSluttdatoEndret = any(),
                )
            } returns deltakerInTest.status

            every {
                vedtakService.opprettEllerOppdaterVedtak(
                    fattetAvNav = false,
                    endretAv = navAnsattInTest,
                    endretAvEnhet = navEnhetInTest,
                    deltaker = any(),
                    fattetDato = null,
                )
            } returns lagVedtak(
                deltakerId = deltakerInTest.id,
                deltakerVedVedtak = deltakerInTest,
            )
            every { arrangorRepository.get(any<String>()) } returns arrangorInTest
            every { deltakerRepository.updateEnkeltplassKladd(any()) } just Runs
            every { deltakerlisteRepository.update(any()) } just Runs

            // Act
            enkeltplassService.meldPaaDirekte(
                deltakerId = deltakerInTest.id,
                decoratedRequest = decoratedRequest,
            )

            // Assert
            verify {
                deltakerService.lagreDeltakerStatus(
                    deltakerId = deltakerInTest.id,
                    nyDeltakerStatus = match { it.type == DeltakerStatus.Type.SOKT_INN },
                    erDeltakerSluttdatoEndret = any(),
                )
            }
            verify {
                vedtakService.opprettEllerOppdaterVedtak(
                    fattetAvNav = false,
                    endretAv = navAnsattInTest,
                    endretAvEnhet = navEnhetInTest,
                    deltaker = any(),
                    fattetDato = null,
                )
            }
            verify {
                outboxService.insertRecord(
                    key = any(),
                    value = ofType<GjennomforingRequestPayload.OpprettEnkeltplass>(),
                    topic = Environment.GJENNOMFORING_REQUEST_TOPIC,
                    suppressOutsideTxWarning = any(),
                )
            }
        }

        @Test
        fun `skal ikke opprette enkeltplass hos Mulighetsrommet for gjennomforing som ikke er enkeltplass`() = runTest {
            // Arrange
            val deltaker = deltakerInTest.copy(
                status = deltakerInTest.status.copy(type = DeltakerStatus.Type.KLADD),
                deltakerliste = deltakerInTest.deltakerliste.copy(
                    gjennomforingstype = GjennomforingType.Gruppe,
                    status = GjennomforingStatusType.KLADD,
                ),
            )
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            // Act
            shouldThrow<IllegalArgumentException> {
                enkeltplassService.meldPaaDirekte(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }

            // Assert
            verify(exactly = 0) {
                outboxService.insertRecord(any(), any(), any(), any())
            }
        }

        @Test
        fun `skal ikke opprette enkeltplass hos Mulighetsrommet for deltaker som ikke er kladd`() = runTest {
            // Arrange
            val deltaker = deltakerInTest.copy(
                status = deltakerInTest.status.copy(type = DeltakerStatus.Type.SOKT_INN),
                deltakerliste = deltakerInTest.deltakerliste.copy(status = GjennomforingStatusType.GJENNOMFORES),
            )
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            // Act
            shouldThrow<IllegalArgumentException> {
                enkeltplassService.meldPaaDirekte(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }

            // Assert
            verify(exactly = 0) {
                outboxService.insertRecord(any(), any(), any(), any())
            }
        }
    }

    companion object {
        private val pameldingRequestInTest = EnkeltplassPameldingRequest(
            beskrivelse = "Testbeskrivelse",
            prisinformasjon = "Test prisinformasjon",
            arrangorUnderenhet = "987654322",
        )

        private val navEnhetInTest = lagNavEnhet(enhetsnummer = "1234")
        private val navAnsattInTest = lagNavAnsatt(navEnhetId = navEnhetInTest.id)

        private val decoratedRequest = EnkeltplassPameldingDecoratedRequest(
            wrappedRequest = pameldingRequestInTest,
            endretAvEnhet = navEnhetInTest.enhetsnummer,
            endretAv = navAnsattInTest.navIdent,
        )

        private val arrangorInTest = Arrangor(
            id = UUID.randomUUID(),
            organisasjonsnummer = pameldingRequestInTest.arrangorUnderenhet,
            navn = "Test Arrangor",
            overordnetArrangorId = null,
        )

        private val deltakerInTest = lagDeltaker(
            navBruker = lagNavBruker(
                navEnhetId = navEnhetInTest.id,
                navVeilederId = navAnsattInTest.id,
            ),
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.KLADD),
            deltakerliste = lagDeltakerliste(
                gjennomforingstype = GjennomforingType.Enkeltplass,
                status = GjennomforingStatusType.KLADD,
                prisinformasjon = "1234",
            ),
        )
    }
}
