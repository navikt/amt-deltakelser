package no.nav.amt.deltaker.enkeltplass

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
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.database.Database.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EnkeltplassServiceTest {
    private val deltakerRepository = mockk<DeltakerRepository>()
    private val deltakerService: DeltakerService = mockk(relaxed = true)
    private val gjennomforingRequestProducer = mockk<GjennomforingRequestProducer>(relaxUnitFun = true)

    private val sut = EnkeltplassService(
        deltakerRepository = deltakerRepository,
        deltakerService = deltakerService,
        gjennomforingRequestProducer = gjennomforingRequestProducer,
    )

    companion object {
        private val deltakerInTest = lagDeltaker(
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

        mockkObject(Database)
        coEvery { transaction<Any>(any()) } answers {
            val block = firstArg<() -> Any>()
            block()
        }
    }

    @AfterEach
    fun cleanup() = unmockkObject(Database)

    @Nested
    inner class OpprettGjennomforingRemoteTests {
        @Test
        fun `skal opprette emkeltplass hos Mulighetsrommet for deltaker i kladd med enkeltplass`() = runTest {
            // Act
            sut.opprettGjennomforingRemote(deltakerId = deltakerInTest.id)

            // Assert
            verify { gjennomforingRequestProducer.produce(any<GjennomforingRequestPayload.OpprettEnkeltplass>()) }
        }

        @Test
        fun `skal ikke opprette emkeltplass hos Mulighetsrommet for gjennomforing som ikke er enkeltplass`() = runTest {
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
            sut.opprettGjennomforingRemote(deltakerId = deltaker.id)

            // Assert
            verify(exactly = 0) { gjennomforingRequestProducer.produce(any<GjennomforingRequestPayload.OpprettEnkeltplass>()) }
        }

        @Test
        fun `skal ikke opprette emkeltplass hos Mulighetsrommet for deltaker som ikke er kladd`() = runTest {
            // Arrange
            val deltaker = deltakerInTest.copy(
                status = deltakerInTest.status.copy(type = DeltakerStatus.Type.SOKT_INN),
                deltakerliste = deltakerInTest.deltakerliste.copy(status = GjennomforingStatusType.GJENNOMFORES),
            )
            every { deltakerRepository.get(deltaker.id) } returns Result.success(deltaker)

            // Act
            sut.opprettGjennomforingRemote(deltakerId = deltaker.id)

            // Assert
            verify(exactly = 0) { gjennomforingRequestProducer.produce(any<GjennomforingRequestPayload.OpprettEnkeltplass>()) }
        }
    }
}
