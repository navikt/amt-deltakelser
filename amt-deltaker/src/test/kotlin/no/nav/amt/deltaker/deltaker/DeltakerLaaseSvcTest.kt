package no.nav.amt.deltaker.deltaker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.utils.TestData.lagImportertFraArena
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerLaaseSvcTest {
    private val mockDeltakerRepository = mockk<DeltakerRepository>(relaxUnitFun = true)
    private val mockImportertFraArenaRepository = mockk<ImportertFraArenaRepository>(relaxUnitFun = true)
    private val sut = DeltakerLaaseSvc(mockDeltakerRepository, mockImportertFraArenaRepository)

    private val deltakerInTest = lagDeltaker()
    private val tidligereDeltakerInTest = lagDeltaker(
        deltakerliste = deltakerInTest.deltakerliste,
        navBruker = deltakerInTest.navBruker,
    )

    private val importertFraArena = lagImportertFraArena(
        deltakerId = tidligereDeltakerInTest.id,
        deltakerVedImport = deltakerInTest.toDeltakerVedImport(LocalDate.now()),
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()

        every { mockImportertFraArenaRepository.getForDeltaker(any()) } returns null

        every {
            mockImportertFraArenaRepository.getForDeltaker(tidligereDeltakerInTest.id)
        } returns importertFraArena
    }

    @Nested
    inner class ErLaastForEndringerTests {
        @Test
        fun `skal kaste feil hvis deltaker ikke finnes`() {
            // Arrange
            every {
                mockDeltakerRepository.getFlereForPerson(
                    personIdent = deltakerInTest.navBruker.personident,
                    deltakerlisteId = deltakerInTest.deltakerliste.id,
                )
            } returns emptyList()

            // Act
            val thrown = shouldThrow<IllegalArgumentException> {
                sut.erLaastForEndringer(deltakerInTest)
            }

            // Assert
            thrown.message shouldBe "Fant ingen deltakelser i deltakerliste med deltaker-id ${deltakerInTest.id}"
        }

        @Test
        fun `skal returrnere false hvis deltaker ikke har tidligere deltakelser`() {
            // Arrange
            every {
                mockDeltakerRepository.getFlereForPerson(
                    personIdent = deltakerInTest.navBruker.personident,
                    deltakerlisteId = deltakerInTest.deltakerliste.id,
                )
            } returns listOf(deltakerInTest)

            // Act
            val result = sut.erLaastForEndringer(deltakerInTest)

            // Assert
            result shouldBe false
        }

        @Test
        fun `skal returrnere true hvis deltaker ikke er nyeste deltaker`() {
            // Arrange
            every {
                mockDeltakerRepository.getFlereForPerson(
                    personIdent = deltakerInTest.navBruker.personident,
                    deltakerlisteId = deltakerInTest.deltakerliste.id,
                )
            } returns listOf(deltakerInTest, tidligereDeltakerInTest)

            // Act
            val result = sut.erLaastForEndringer(deltakerInTest)

            // Assert
            result shouldBe true
        }

        @Test
        fun `skal returnere true hvis aktiv Arena-deltakelse finnes`() {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )

            val deltaker2 = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                navBruker = deltaker.navBruker,
                deltakerliste = deltaker.deltakerliste,
            )

            every {
                mockDeltakerRepository.getFlereForPerson(
                    personIdent = deltaker.navBruker.personident,
                    deltakerlisteId = deltaker.deltakerliste.id,
                )
            } returns listOf(deltaker, deltaker2)

            // Act
            val result = sut.erLaastForEndringer(deltaker)

            // Assert
            result shouldBe true
        }
    }

    @Nested
    inner class GetPaameldtTidspunktTests {
        @Test
        fun `skal returnere null hvis deltaker ikke har vedtak eller importert fra Arena`() {
            // Arrange
            every { mockImportertFraArenaRepository.getForDeltaker(any()) } returns null

            // Act
            val result = sut.getPaameldtTidspunkt(tidligereDeltakerInTest)

            // Assert
            result shouldBe null
        }

        @Test
        fun `skal returnere fattet tidspunkt hvis deltaker har vedtak`() {
            // Arrange
            val vedtakInTest = lagVedtak(deltakerVedVedtak = deltakerInTest)
            val tidligereDeltaker = tidligereDeltakerInTest.copy(vedtaksinformasjon = vedtakInTest.tilVedtaksInformasjon())

            every { mockImportertFraArenaRepository.getForDeltaker(any()) } returns null

            // Act
            val result = sut.getPaameldtTidspunkt(tidligereDeltaker)

            // Assert
            result shouldBe vedtakInTest.fattet
        }

        @Test
        fun `skal returnere innsoktDatoAsLocalDateTime fra Arena hvis deltaker er importert fra Arena`() {
            // Arrange
            every {
                mockImportertFraArenaRepository.getForDeltaker(tidligereDeltakerInTest.id)
            } returns importertFraArena

            // Act
            val result = sut.getPaameldtTidspunkt(tidligereDeltakerInTest)

            // Assert
            result shouldBe importertFraArena.innsoktDatoAsLocalDateTime
        }
    }
}
