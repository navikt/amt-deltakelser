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
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.database.Database.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
        coEvery { transaction<Any>(any()) } answers {
            val block = firstArg<() -> Any>()
            block()
        }
    }

    @AfterEach
    fun cleanup() = unmockkObject(Database)

    @Nested
    inner class OppdaterKladdTests {
        @Test
        fun `skal kaste exception for deltaker som ikke er enkeltplass`() = runTest {
            // Arrange
            val deltaker = deltakerInTest.copy(
                deltakerliste = deltakerInTest.deltakerliste.copy(gjennomforingstype = GjennomforingType.Gruppe),
            )
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                enkeltplassService.oppdaterKladd(
                    deltakerId = deltaker.id,
                    startdato = null,
                    sluttdato = null,
                    beskrivelse = null,
                    prisinformasjon = null,
                )
            }
        }
    }

    @Nested
    inner class MeldPaaDirekteTests {
        @Test
        fun `skal opprette enkeltplass hos Mulighetsrommet for deltaker i kladd med enkeltplass`() = runTest {
            // Arrange
            every {
                deltakerService.lagreDeltakerStatus(
                    deltakerId = deltakerInTest.id,
                    nyDeltakerStatus = match { it.type == DeltakerStatus.Type.SOKT_INN },
                    erDeltakerSluttdatoEndret = true,
                )
            } just Runs

            every {
                vedtakService.opprettEllerOppdaterVedtak(
                    fattetAvNav = any(),
                    endretAv = any(),
                    endretAvEnhet = any(),
                    deltaker = any(),
                    fattetDato = any(),
                )
            } returns lagVedtak(
                deltakerId = deltakerInTest.id,
                deltakerVedVedtak = deltakerInTest,
            )

            every { deltakerRepository.updateEnkeltplassKladd(any()) } just Runs
            every { deltakerlisteRepository.update(any()) } just Runs

            // Act
            enkeltplassService.meldPaaDirekte(
                deltakerId = deltakerInTest.id,
                decoratedRequest = decoratedRequest,
            )

            // Assert
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
            arrangorOrgnummer = "987654321",
        )

        private val navEnhetInTest = lagNavEnhet(enhetsnummer = "1234")
        private val navAnsattInTest = lagNavAnsatt(navEnhetId = navEnhetInTest.id)

        private val decoratedRequest = EnkeltplassPameldingDecoratedRequest(
            wrappedRequest = pameldingRequestInTest,
            endretAvEnhet = navEnhetInTest.enhetsnummer,
            endretAv = navAnsattInTest.navIdent,
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
