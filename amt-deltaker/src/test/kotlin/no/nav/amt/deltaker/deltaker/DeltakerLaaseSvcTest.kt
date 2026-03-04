@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagImportertFraArena
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year

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

    companion object {
        @RegisterExtension
        private val dbExtension = DatabaseTestExtension()
    }

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

    @Nested
    inner class OppdaterDeltakerLaasTests {
        private val deltakerRepository = DeltakerRepository()
        private val mockImportertFraArenaRepository = mockk<ImportertFraArenaRepository>(relaxUnitFun = true)

        private val deltakerLaaseSvc = DeltakerLaaseSvc(
            deltakerRepository = deltakerRepository,
            importertFraArenaRepository = mockImportertFraArenaRepository,
        )

        private val innsoktDatoInTest = LocalDate.of(Year.now().value - 1, 2, 18)

        @BeforeEach
        fun setup() {
            clearAllMocks()
            every { mockImportertFraArenaRepository.getForDeltaker(any()) } returns null
        }

        @Test
        fun `oppdaterDeltakerLaas - ny deltaker - beholder låsing`() {
            // Arrange
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)

            deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe true

            // Act
            deltakerLaaseSvc.oppdaterDeltakerLaas(
                deltakerId = deltaker.id,
                personident = deltaker.navBruker.personident,
                deltakerlisteId = deltaker.deltakerliste.id,
            )

            // Assert
            deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe true
        }

        @Test
        fun `oppdaterDeltakerLaas - importerte deltakere med samme innsøktDato, endring på nyeste deltaker - beholder låsing`() {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    gyldigFra = LocalDateTime.now().minusDays(1),
                ),
                kanEndres = true,
            )
            TestRepository.insert(deltaker)

            val historisertDeltaker = lagDeltaker(
                navBruker = deltaker.navBruker,
                deltakerliste = deltaker.deltakerliste,
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    gyldigFra = LocalDateTime.now().minusDays(2),
                ),
                kanEndres = false,
            )

            TestRepository.insert(historisertDeltaker)

            every {
                mockImportertFraArenaRepository.getForDeltaker(deltaker.id)
            } returns lagImportertFraArena(
                deltaker = deltaker,
                innsoktDato = innsoktDatoInTest,
            )

            every {
                mockImportertFraArenaRepository.getForDeltaker(historisertDeltaker.id)
            } returns lagImportertFraArena(
                deltaker = historisertDeltaker,
                innsoktDato = innsoktDatoInTest,
            )

            // Act
            deltakerLaaseSvc.oppdaterDeltakerLaas(
                deltakerId = deltaker.id,
                personident = deltaker.navBruker.personident,
                deltakerlisteId = deltaker.deltakerliste.id,
            )

            // Assert
            deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe true
            deltakerRepository.get(historisertDeltaker.id).getOrThrow().kanEndres shouldBe false

            verifyAll {
                mockImportertFraArenaRepository.getForDeltaker(deltaker.id)
                mockImportertFraArenaRepository.getForDeltaker(historisertDeltaker.id)
            }
        }

        @Test
        fun `oppdaterDeltakerLaas - importerte deltakere med samme innsøktDato, endring på historisert deltaker - beholder låsing`() {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    gyldigFra = LocalDateTime.now().minusDays(1),
                ),
            )
            TestRepository.insert(deltaker)

            val historisertDeltaker = lagDeltaker(
                navBruker = deltaker.navBruker,
                deltakerliste = deltaker.deltakerliste,
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    gyldigFra = LocalDateTime.now().minusDays(2),
                ),
                kanEndres = false,
            )
            TestRepository.insert(historisertDeltaker)

            every {
                mockImportertFraArenaRepository.getForDeltaker(deltaker.id)
            } returns lagImportertFraArena(
                deltaker = deltaker,
                innsoktDato = innsoktDatoInTest,
            )

            every {
                mockImportertFraArenaRepository.getForDeltaker(historisertDeltaker.id)
            } returns lagImportertFraArena(
                deltaker = historisertDeltaker,
                innsoktDato = innsoktDatoInTest,
            )

            // Act
            deltakerLaaseSvc.oppdaterDeltakerLaas(
                deltakerId = historisertDeltaker.id,
                personident = historisertDeltaker.navBruker.personident,
                deltakerlisteId = historisertDeltaker.deltakerliste.id,
            )

            // Assert
            deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe true
            deltakerRepository.get(historisertDeltaker.id).getOrThrow().kanEndres shouldBe false

            verifyAll {
                mockImportertFraArenaRepository.getForDeltaker(deltaker.id)
                mockImportertFraArenaRepository.getForDeltaker(historisertDeltaker.id)
            }
        }

        @Test
        fun `oppdaterDeltakerLaas - flere deltakelser på samme deltakerliste - låser den eldste`() {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    gyldigFra = LocalDateTime.now().minusDays(10),
                ),
            )
            TestRepository.insert(deltaker)

            val deltaker2 = lagDeltaker(
                navBruker = deltaker.navBruker,
                deltakerliste = deltaker.deltakerliste,
            )
            TestRepository.insert(deltaker2)

            // Act
            deltakerLaaseSvc.oppdaterDeltakerLaas(
                deltakerId = deltaker.id,
                personident = deltaker.navBruker.personident,
                deltakerlisteId = deltaker.deltakerliste.id,
            )

            // Assert
            deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe false
            deltakerRepository.get(deltaker2.id).getOrThrow().kanEndres shouldBe true
        }

        @Test
        fun `oppdaterDeltakerLaas - flere deltakelser på samme deltakerliste, nyeste er feilregistrert - låser begge`() {
            // Arrange
            val harSluttetDeltaker = lagDeltaker(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    gyldigFra = LocalDateTime.now().minusDays(10),
                ),
            )
            TestRepository.insert(harSluttetDeltaker)

            val feilregistrertdeltaker = lagDeltaker(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.FEILREGISTRERT,
                    gyldigFra = LocalDateTime.now().minusDays(3),
                ),
                navBruker = harSluttetDeltaker.navBruker,
                deltakerliste = harSluttetDeltaker.deltakerliste,
            )
            TestRepository.insert(feilregistrertdeltaker)

            // Act
            deltakerLaaseSvc.oppdaterDeltakerLaas(
                deltakerId = harSluttetDeltaker.id,
                personident = harSluttetDeltaker.navBruker.personident,
                deltakerlisteId = harSluttetDeltaker.deltakerliste.id,
            )

            // Assert
            deltakerRepository.get(harSluttetDeltaker.id).getOrThrow().kanEndres shouldBe false
            deltakerRepository.get(feilregistrertdeltaker.id).getOrThrow().kanEndres shouldBe false
        }

        @Test
        fun `oppdaterDeltakerLaas - flere deltakelser på samme deltakerliste med samme reg dato - låser den med avsluttende status`() {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(deltaker)

            val deltaker2 = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                navBruker = deltaker.navBruker,
                deltakerliste = deltaker.deltakerliste,
            )
            TestRepository.insert(deltaker2)

            // Act
            deltakerLaaseSvc.oppdaterDeltakerLaas(
                deltakerId = deltaker.id,
                personident = deltaker.navBruker.personident,
                deltakerlisteId = deltaker.deltakerliste.id,
            )

            // Assert
            deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe false
            deltakerRepository.get(deltaker2.id).getOrThrow().kanEndres shouldBe true
        }

        @Test
        fun `oppdaterDeltakerLaas - flere deltakelser på samme deltakerliste med samme reg dato - kaster exception`() {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)

            val deltaker2 = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                navBruker = deltaker.navBruker,
                deltakerliste = deltaker.deltakerliste,
            )
            TestRepository.insert(deltaker2)

            // Act & Assert
            assertThrows<IllegalStateException> {
                deltakerLaaseSvc.oppdaterDeltakerLaas(
                    deltakerId = deltaker.id,
                    personident = deltaker.navBruker.personident,
                    deltakerlisteId = deltaker.deltakerliste.id,
                )
            }
        }
    }
}
