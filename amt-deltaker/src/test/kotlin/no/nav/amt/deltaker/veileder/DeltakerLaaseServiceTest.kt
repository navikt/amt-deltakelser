package no.nav.amt.deltaker.veileder

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
        fun `skal returnere false hvis deltaker ikke har tidligere deltakelser`() {
            // Arrange
            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    setOf(deltakerInTest.navBruker.personident),
                    deltakerInTest.deltakerliste.id,
                )
            } returns mapOf(
                deltakerInTest.navBruker.personident to listOf(
                    laaseInfo(deltakerInTest.id, deltakerInTest.navBruker.personident),
                ),
            )

            // Act
            val result = sut.erLaastForEndringer(deltakerInTest)

            // Assert
            result shouldBe false
        }

        @Test
        fun `skal returnere true hvis deltaker ikke er nyeste deltaker`() {
            // Arrange
            val personident = deltakerInTest.navBruker.personident
            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    setOf(personident),
                    deltakerInTest.deltakerliste.id,
                )
            } returns mapOf(
                personident to listOf(
                    laaseInfo(
                        id = deltakerInTest.id,
                        personident = personident,
                        statusType = DeltakerStatus.Type.DELTAR,
                        statusGyldigFra = LocalDateTime.now().minusDays(1),
                        vedtakFattet = LocalDateTime.now().minusDays(1),
                    ),
                    laaseInfo(
                        id = tidligereDeltakerInTest.id,
                        personident = personident,
                        statusType = DeltakerStatus.Type.HAR_SLUTTET,
                        statusGyldigFra = LocalDateTime.now().minusMonths(2),
                        vedtakFattet = LocalDateTime.now().minusMonths(2),
                    ),
                ),
            )

            // Act
            val result = sut.erLaastForEndringer(tidligereDeltakerInTest)

            // Assert
            result shouldBe true
        }

        @Test
        fun `enkelt-deltaker-varianten bruker samme spissede SQL-spoerring som bulk`() {
            // Arrange
            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(any(), any())
            } returns mapOf(
                deltakerInTest.navBruker.personident to listOf(
                    laaseInfo(deltakerInTest.id, deltakerInTest.navBruker.personident),
                ),
            )

            // Act
            sut.erLaastForEndringer(deltakerInTest)

            // Assert
            verify(exactly = 1) {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    setOf(deltakerInTest.navBruker.personident),
                    deltakerInTest.deltakerliste.id,
                )
            }
        }
    }

    @Nested
    inner class ErLaastForEndringerForDeltakereTests {
        @Test
        fun `skal returnere tom map naar deltakere er tom liste`() {
            // Act
            val result = sut.erLaastForEndringerForDeltakere(
                deltakerIdToPersonIdentMap = emptyMap(),
                gjennomforingId = UUID.randomUUID(),
            )

            // Assert
            result shouldBe emptyMap()
            verify(exactly = 0) { mockDeltakerRepository.getDeltakelserForLaaseSjekk(any(), any()) }
        }

        @Test
        fun `skal returnere false naar personen kun har en deltakelse`() {
            // Arrange
            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    setOf(deltakerInTest.navBruker.personident),
                    deltakerInTest.deltakerliste.id,
                )
            } returns mapOf(
                deltakerInTest.navBruker.personident to listOf(
                    laaseInfo(deltakerInTest.id, deltakerInTest.navBruker.personident),
                ),
            )

            // Act
            val result = sut.erLaastForEndringerForDeltakere(
                deltakerIdToPersonIdentMap = mapOf(deltakerInTest.id to deltakerInTest.navBruker.personident),
                gjennomforingId = deltakerInTest.deltakerliste.id,
            )

            // Assert
            result shouldBe mapOf(deltakerInTest.id to false)
        }

        @Test
        fun `skal laase eldre deltakelse og frigi nyeste aktive`() {
            // Arrange
            val personident = deltakerInTest.navBruker.personident
            val nyeste = deltakerInTest
            val eldre = tidligereDeltakerInTest

            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    setOf(personident),
                    nyeste.deltakerliste.id,
                )
            } returns mapOf(
                personident to listOf(
                    laaseInfo(
                        id = nyeste.id,
                        personident = personident,
                        statusType = DeltakerStatus.Type.DELTAR,
                        statusGyldigFra = LocalDateTime.now().minusDays(1),
                        vedtakFattet = LocalDateTime.now().minusDays(1),
                    ),
                    laaseInfo(
                        id = eldre.id,
                        personident = personident,
                        statusType = DeltakerStatus.Type.HAR_SLUTTET,
                        statusGyldigFra = LocalDateTime.now().minusMonths(2),
                        vedtakFattet = LocalDateTime.now().minusMonths(2),
                    ),
                ),
            )

            // Act
            val result = sut.erLaastForEndringerForDeltakere(
                deltakerIdToPersonIdentMap = mapOf(
                    eldre.id to eldre.navBruker.personident,
                    nyeste.id to nyeste.navBruker.personident,
                ),
                gjennomforingId = nyeste.deltakerliste.id,
            )

            // Assert
            result shouldBe mapOf(
                nyeste.id to false,
                eldre.id to true,
            )
        }

        @Test
        fun `skal prioritere aktiv status foran nyere avsluttet status`() {
            // Arrange
            val personident = deltakerInTest.navBruker.personident
            val aktiv = deltakerInTest
            val nyereAvsluttet = tidligereDeltakerInTest

            every {
                mockDeltakerRepository.getDeltakelserForLaaseSjekk(
                    setOf(personident),
                    aktiv.deltakerliste.id,
                )
            } returns mapOf(
                personident to listOf(
                    // Avsluttet er nyere i tid, men ikke aktiv
                    laaseInfo(
                        id = nyereAvsluttet.id,
                        personident = personident,
                        statusType = DeltakerStatus.Type.HAR_SLUTTET,
                        statusGyldigFra = LocalDateTime.now(),
                        vedtakFattet = LocalDateTime.now(),
                    ),
                    // Aktiv, men eldre
                    laaseInfo(
                        id = aktiv.id,
                        personident = personident,
                        statusType = DeltakerStatus.Type.DELTAR,
                        statusGyldigFra = LocalDateTime.now().minusMonths(1),
                        vedtakFattet = LocalDateTime.now().minusMonths(1),
                    ),
                ),
            )

            // Act
            val result = sut.erLaastForEndringerForDeltakere(
                deltakerIdToPersonIdentMap = mapOf(
                    aktiv.id to aktiv.navBruker.personident,
                    nyereAvsluttet.id to nyereAvsluttet.navBruker.personident,
                ),
                gjennomforingId = aktiv.deltakerliste.id,
            )

            // Assert
            result shouldBe mapOf(
                aktiv.id to false,
                nyereAvsluttet.id to true,
            )
        }
    }

    private fun laaseInfo(
        id: UUID,
        personident: String,
        statusType: DeltakerStatus.Type = DeltakerStatus.Type.DELTAR,
        statusGyldigFra: LocalDateTime = LocalDateTime.now(),
        vedtakFattet: LocalDateTime? = null,
        innsoektDatoFraArena: LocalDate? = null,
    ) = DeltakelseLaaseInfo(
        id = id,
        personident = personident,
        statusType = statusType,
        statusGyldigFra = statusGyldigFra,
        vedtakFattet = vedtakFattet,
        innsoektDatoFraArena = innsoektDatoFraArena,
    )
}
