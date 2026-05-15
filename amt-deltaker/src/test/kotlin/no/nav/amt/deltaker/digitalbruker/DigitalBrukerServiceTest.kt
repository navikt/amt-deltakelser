package no.nav.amt.deltaker.digitalbruker

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DigitalBrukerServiceTest {
    private val amtDistribusjonClient: AmtDistribusjonClient = mockk()
    private val service = DigitalBrukerService(amtDistribusjonClient)

    @BeforeEach
    fun setup() {
        mockkObject(DigitalBrukerCacheRepository)
        every { DigitalBrukerCacheRepository.upsertBatch(any()) } returns Unit
    }

    @AfterEach
    fun teardown() {
        unmockkObject(DigitalBrukerCacheRepository)
    }

    @Test
    fun `hentErDigitalForPersonidenter - tom input - returnerer tomt map og kaller hverken cache eller klient`() = runTest {
        // Act
        val resultat = service.hentErDigitalForPersonidenter(emptySet())

        // Assert
        resultat shouldBe emptyMap()
        verify(exactly = 0) { DigitalBrukerCacheRepository.hentForPersonidenter(any()) }
        verify(exactly = 0) { DigitalBrukerCacheRepository.upsertBatch(any()) }
        coVerify(exactly = 0) { amtDistribusjonClient.digitalBruker(any()) }
    }

    @Test
    fun `hentErDigitalForPersonidenter - alt finnes i cache - kaller ikke klient og upserter ikke`() = runTest {
        // Arrange
        val personidenter = setOf("11111111111", "22222222222")
        every { DigitalBrukerCacheRepository.hentForPersonidenter(personidenter) } returns mapOf(
            "11111111111" to DigitalBrukerCacheEntry("11111111111", true),
            "22222222222" to DigitalBrukerCacheEntry("22222222222", false),
        )

        // Act
        val resultat = service.hentErDigitalForPersonidenter(personidenter)

        // Assert
        resultat shouldContainExactly mapOf(
            "11111111111" to true,
            "22222222222" to false,
        )
        coVerify(exactly = 0) { amtDistribusjonClient.digitalBruker(any()) }
        verify(exactly = 0) { DigitalBrukerCacheRepository.upsertBatch(any()) }
    }

    @Test
    fun `hentErDigitalForPersonidenter - ingenting i cache - henter alt fra klient og upserter`() = runTest {
        // Arrange
        val personidenter = setOf("11111111111", "22222222222")
        every { DigitalBrukerCacheRepository.hentForPersonidenter(personidenter) } returns emptyMap()
        coEvery { amtDistribusjonClient.digitalBruker("11111111111") } returns true
        coEvery { amtDistribusjonClient.digitalBruker("22222222222") } returns false

        // Act
        val resultat = service.hentErDigitalForPersonidenter(personidenter)

        // Assert
        resultat shouldContainExactly mapOf(
            "11111111111" to true,
            "22222222222" to false,
        )
        coVerify(exactly = 1) { amtDistribusjonClient.digitalBruker("11111111111") }
        coVerify(exactly = 1) { amtDistribusjonClient.digitalBruker("22222222222") }
        verify(exactly = 1) {
            DigitalBrukerCacheRepository.upsertBatch(
                match { it.toSet() == setOf("11111111111" to true, "22222222222" to false) },
            )
        }
    }

    @Test
    fun `hentErDigitalForPersonidenter - delvis cache - kaller klient kun for manglende og kombinerer resultat`() = runTest {
        // Arrange
        val personidenter = setOf("11111111111", "22222222222", "33333333333")
        every { DigitalBrukerCacheRepository.hentForPersonidenter(personidenter) } returns mapOf(
            "11111111111" to DigitalBrukerCacheEntry("11111111111", true),
        )
        coEvery { amtDistribusjonClient.digitalBruker("22222222222") } returns false
        coEvery { amtDistribusjonClient.digitalBruker("33333333333") } returns true

        // Act
        val resultat = service.hentErDigitalForPersonidenter(personidenter)

        // Assert
        resultat shouldContainExactly mapOf(
            "11111111111" to true,
            "22222222222" to false,
            "33333333333" to true,
        )
        coVerify(exactly = 0) { amtDistribusjonClient.digitalBruker("11111111111") }
        coVerify(exactly = 1) { amtDistribusjonClient.digitalBruker("22222222222") }
        coVerify(exactly = 1) { amtDistribusjonClient.digitalBruker("33333333333") }
        verify(exactly = 1) {
            DigitalBrukerCacheRepository.upsertBatch(
                match { it.toSet() == setOf("22222222222" to false, "33333333333" to true) },
            )
        }
    }

    @Test
    fun `erDigital - finnes i cache - returnerer cached verdi og kaller ikke klient`() = runTest {
        // Arrange
        val personident = "11111111111"
        every { DigitalBrukerCacheRepository.hentForPersonidenter(setOf(personident)) } returns mapOf(
            personident to DigitalBrukerCacheEntry(personident, true),
        )

        // Act
        val resultat = service.erDigital(personident)

        // Assert
        resultat shouldBe true
        coVerify(exactly = 0) { amtDistribusjonClient.digitalBruker(any()) }
    }

    @Test
    fun `erDigital - ikke i cache - henter fra klient og upserter`() = runTest {
        // Arrange
        val personident = "11111111111"
        every { DigitalBrukerCacheRepository.hentForPersonidenter(setOf(personident)) } returns emptyMap()
        coEvery { amtDistribusjonClient.digitalBruker(personident) } returns false

        // Act
        val resultat = service.erDigital(personident)

        // Assert
        resultat shouldBe false
        coVerify(exactly = 1) { amtDistribusjonClient.digitalBruker(personident) }
        verify(exactly = 1) {
            DigitalBrukerCacheRepository.upsertBatch(listOf(personident to false))
        }
    }
}
