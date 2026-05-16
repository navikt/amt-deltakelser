package no.nav.amt.deltaker.veileder

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.deltaker.repository.DeltakelseLaaseInfo
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerLaaseServiceTest {
    private val mockDeltakerRepository = mockk<DeltakerRepository>()
    private val sut = DeltakerLaaseService(mockDeltakerRepository)

    private val deltakerInTest = lagDeltaker()
    private val tidligereDeltakerInTest = lagDeltaker(
        deltakerliste = deltakerInTest.deltakerliste,
        navBruker = deltakerInTest.navBruker,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Nested
    inner class ErLaastForEndringerTests {
        @Test
        fun `skal kaste feil hvis deltaker ikke finnes`() {
            // Arrange
            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    personident = deltakerInTest.navBruker.personident,
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
        fun `skal returnere false hvis deltaker ikke har tidligere deltakelser`() {
            // Arrange
            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    personident = deltakerInTest.navBruker.personident,
                    deltakerlisteId = deltakerInTest.deltakerliste.id,
                )
            } returns listOf(
                laaseInfo(deltakerInTest.id),
            )

            // Act
            val result = sut.erLaastForEndringer(deltakerInTest)

            // Assert
            result shouldBe false
        }

        @Test
        fun `skal returnere true hvis deltaker ikke er nyeste deltaker`() {
            // Arrange
            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    personident = deltakerInTest.navBruker.personident,
                    deltakerlisteId = deltakerInTest.deltakerliste.id,
                )
            } returns listOf(
                laaseInfo(
                    id = deltakerInTest.id,
                    statusType = DeltakerStatus.Type.DELTAR,
                    statusGyldigFra = LocalDateTime.now().minusDays(1),
                    vedtakFattet = LocalDateTime.now().minusDays(1),
                ),
                laaseInfo(
                    id = tidligereDeltakerInTest.id,
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    statusGyldigFra = LocalDateTime.now().minusMonths(2),
                    vedtakFattet = LocalDateTime.now().minusMonths(2),
                ),
            )

            // Act
            val result = sut.erLaastForEndringer(tidligereDeltakerInTest)

            // Assert
            result shouldBe true
        }

        @Test
        fun `bruker spisset SQL-spoerring med enkelt personident`() {
            // Arrange
            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    personident = any(),
                    deltakerlisteId = any(),
                )
            } returns listOf(
                laaseInfo(deltakerInTest.id),
            )

            // Act
            sut.erLaastForEndringer(deltakerInTest)

            // Assert
            verify(exactly = 1) {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    deltakerInTest.navBruker.personident,
                    deltakerInTest.deltakerliste.id,
                )
            }
        }
    }

    @Nested
    inner class PrioriteringTests {
        @Test
        fun `skal prioritere aktiv status foran nyere avsluttet status`() {
            // Arrange
            val aktiv = deltakerInTest
            val nyereAvsluttet = tidligereDeltakerInTest

            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    personident = aktiv.navBruker.personident,
                    deltakerlisteId = aktiv.deltakerliste.id,
                )
            } returns listOf(
                laaseInfo(
                    id = nyereAvsluttet.id,
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    statusGyldigFra = LocalDateTime.now(),
                    vedtakFattet = LocalDateTime.now(),
                ),
                laaseInfo(
                    id = aktiv.id,
                    statusType = DeltakerStatus.Type.DELTAR,
                    statusGyldigFra = LocalDateTime.now().minusMonths(1),
                    vedtakFattet = LocalDateTime.now().minusMonths(1),
                ),
            )

            // Act + Assert: aktiv låses ikke, nyere avsluttet låses
            sut.erLaastForEndringer(aktiv) shouldBe false
            sut.erLaastForEndringer(nyereAvsluttet) shouldBe true
        }

        @Test
        fun `skal laase eldre deltakelse og frigi nyeste aktive`() {
            // Arrange
            val nyeste = deltakerInTest
            val eldre = tidligereDeltakerInTest

            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    personident = nyeste.navBruker.personident,
                    deltakerlisteId = nyeste.deltakerliste.id,
                )
            } returns listOf(
                laaseInfo(
                    id = nyeste.id,
                    statusType = DeltakerStatus.Type.DELTAR,
                    statusGyldigFra = LocalDateTime.now().minusDays(1),
                    vedtakFattet = LocalDateTime.now().minusDays(1),
                ),
                laaseInfo(
                    id = eldre.id,
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    statusGyldigFra = LocalDateTime.now().minusMonths(2),
                    vedtakFattet = LocalDateTime.now().minusMonths(2),
                ),
            )

            sut.erLaastForEndringer(nyeste) shouldBe false
            sut.erLaastForEndringer(eldre) shouldBe true
        }
    }

    private fun laaseInfo(
        id: UUID,
        statusType: DeltakerStatus.Type = DeltakerStatus.Type.DELTAR,
        statusGyldigFra: LocalDateTime = LocalDateTime.now(),
        vedtakFattet: LocalDateTime? = null,
        innsoektDatoFraArena: LocalDate? = null,
    ) = DeltakelseLaaseInfo(
        id = id,
        statusType = statusType,
        statusGyldigFra = statusGyldigFra,
        vedtakFattet = vedtakFattet,
        innsoektDatoFraArena = innsoektDatoFraArena,
    )
}
