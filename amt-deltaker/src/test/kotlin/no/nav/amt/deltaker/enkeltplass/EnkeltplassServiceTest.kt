package no.nav.amt.deltaker.enkeltplass

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
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

class EnkeltplassServiceTest {
    private val deltakerRepository = mockk<DeltakerRepository>(relaxed = true)
    private val deltakerService: DeltakerService = mockk(relaxed = true)
    private val gjennomforingRequestProducer = mockk<GjennomforingRequestProducer>(relaxUnitFun = true)
    private val deltakerlisteRepository = mockk<DeltakerlisteRepository>(relaxed = true)
    private val navBrukerService = mockk<NavBrukerService>()
    private val tiltakRepository = mockk<TiltakstypeRepository>()
    private val navEnhetservice = mockk<NavEnhetService>()
    private val navAnsattService = mockk<NavAnsattService>()
    private val vedtakService = mockk<VedtakService>(relaxed = true)

    private val sut = EnkeltplassService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        gjennomforingRequestProducer = gjennomforingRequestProducer,
        deltakerlisteRepository = deltakerlisteRepository,
        navBrukerService = navBrukerService,
        tiltakRepository = tiltakRepository,
        navEnhetService = navEnhetservice,
        navAnsattService = navAnsattService,
        vedtakService = vedtakService,
    )

    companion object {
        private val request = EnkeltplassPameldingRequest(
            beskrivelse = "Testbeskrivelse",
            prisinformasjon = "Test prisinformasjon",
            arrangorOrgnummer = "987654321",
        )

        private val decoratedRequest = EnkeltplassPameldingDecoratedRequest(
            wrappedRequest = request,
            endretAvEnhet = "1234",
            endretAv = "123456789",
        )

        private val navEnhetInTest = lagNavEnhet()
        private val navAnsattInTest = lagNavAnsatt(navEnhetId = navEnhetInTest.id)

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

    @BeforeEach
    fun setup() {
        clearAllMocks()

        every { deltakerRepository.get(deltakerInTest.id) } returns Result.success(deltakerInTest)
        every {
            deltakerService.lagreDeltakerStatus(
                deltakerId = deltakerInTest.id,
                nyDeltakerStatus = match { it.type == DeltakerStatus.Type.SOKT_INN },
                erDeltakerSluttdatoEndret = false,
            )
        } just runs

        coEvery {
            navEnhetservice.hentEllerOpprettNavEnhet(decoratedRequest.endretAvEnhet)
        } returns navEnhetInTest

        coEvery {
            navAnsattService.hentEllerOpprettNavAnsatt(decoratedRequest.endretAv)
        } returns navAnsattInTest

        mockkObject(Database)
        coEvery { transaction<Any>(any()) } answers {
            val block = firstArg<() -> Any>()
            block()
        }
    }

    @AfterEach
    fun cleanup() = unmockkObject(Database)

    @Nested
    inner class MeldPaaDirekteTests {
        @Test
        fun `skal opprette enkeltplass hos Mulighetsrommet for deltaker i kladd med enkeltplass`() = runTest {
            // Arrange

            // Act
            sut.meldPaaDirekte(deltakerId = deltakerInTest.id, decoratedRequest = decoratedRequest)

            // Assert
            verify { gjennomforingRequestProducer.produce(any<GjennomforingRequestPayload.OpprettEnkeltplass>()) }
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
                sut.meldPaaDirekte(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }
            // Assert
            verify(exactly = 0) { gjennomforingRequestProducer.produce(any<GjennomforingRequestPayload.OpprettEnkeltplass>()) }
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
                sut.meldPaaDirekte(deltakerId = deltaker.id, decoratedRequest = decoratedRequest)
            }

            // Assert
            verify(exactly = 0) { gjennomforingRequestProducer.produce(any<GjennomforingRequestPayload.OpprettEnkeltplass>()) }
        }
    }
}
