@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagImportertFraArena
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DeltakerLaaseSvcTest {
    private val mockDeltakerRepository = mockk<DeltakerRepository>(relaxUnitFun = true)
    private val mockImportertFraArenaRepository = mockk<ImportertFraArenaRepository>(relaxUnitFun = true)
    private val sut = DeltakerLaaseSvc(mockDeltakerRepository, mockImportertFraArenaRepository)

    private val deltakerInTest = lagDeltaker()
    private val tidligereDeltakerInTest = lagDeltaker(
        deltakerliste = deltakerInTest.deltakerliste,
        navBruker = deltakerInTest.navBruker,
    )
    private val importertFraArena = lagImportertFraArena(deltaker = tidligereDeltakerInTest)

    @BeforeEach
    fun setup() {
        clearAllMocks()

        every {
            mockDeltakerRepository.getFlereForPerson(
                personIdent = deltakerInTest.navBruker.personident,
                deltakerlisteId = deltakerInTest.deltakerliste.id,
            )
        } returns listOf(deltakerInTest, tidligereDeltakerInTest)

        every {
            mockImportertFraArenaRepository.getForDeltaker(tidligereDeltakerInTest.id)
        } returns importertFraArena
    }

    @Nested
    inner class LaasOppForrigeDeltakerTests {
        @Test
        fun `skal ikke feile hvis deltaker ikke har tidligere deltakelser`() {
            // Arrange
            every { mockImportertFraArenaRepository.getForDeltaker(any()) } returns null

            // Act
            val result = sut.laasOppForrigeDeltaker(deltakerInTest)

            // Assert
            result shouldBe null

            verify(exactly = 0) {
                mockDeltakerRepository.settKanEndres(
                    deltakerId = any(),
                    kanEndres = any(),
                )
            }
        }

        @Test
        fun `person har tidligere deltakelser fra Arena - laser opp forrige deltaker`() {
            // Act
            val result = sut.laasOppForrigeDeltaker(deltakerInTest)

            // Assert
            result shouldBe tidligereDeltakerInTest.id

            verify {
                mockDeltakerRepository.settKanEndres(
                    deltakerId = tidligereDeltakerInTest.id,
                    kanEndres = true,
                )
            }
        }

        @Test
        fun `person har tidligere deltakelser med fattet vedtak - laser opp forrige deltaker`() {
            // Arrange
            val vedtakInTest = lagVedtak(deltakerVedVedtak = tidligereDeltakerInTest, fattet = LocalDateTime.now())
            val tidligereDeltaker = tidligereDeltakerInTest.copy(vedtaksinformasjon = vedtakInTest.tilVedtaksInformasjon())

            every { mockImportertFraArenaRepository.getForDeltaker(any()) } returns null
            every {
                mockDeltakerRepository.getFlereForPerson(
                    personIdent = deltakerInTest.navBruker.personident,
                    deltakerlisteId = deltakerInTest.deltakerliste.id,
                )
            } returns listOf(deltakerInTest, tidligereDeltaker)

            // Act
            val result = sut.laasOppForrigeDeltaker(deltakerInTest)

            // Assert
            result shouldBe tidligereDeltakerInTest.id

            verify {
                mockDeltakerRepository.settKanEndres(
                    deltakerId = tidligereDeltaker.id,
                    kanEndres = true,
                )
            }
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
        fun `skal returnere innsoktDato fra Arena hvis deltaker er importert fra Arena`() {
            // Arrange
            every {
                mockImportertFraArenaRepository.getForDeltaker(tidligereDeltakerInTest.id)
            } returns importertFraArena

            // Act
            val result = sut.getPaameldtTidspunkt(tidligereDeltakerInTest)

            // Assert
            result shouldBe importertFraArena.deltakerVedImport.innsoktDato.atStartOfDay()
        }
    }
}
