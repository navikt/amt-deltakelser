package no.nav.amt.deltaker.navansatt

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavAnsattRepositoryTest {
    private val navAnsattRepository = NavAnsattRepository()
    private val navEnhetRepository = NavEnhetRepository()

    private val navEnhet = lagNavEnhet()

    companion object {
        @RegisterExtension
        private val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(navEnhet)
    }

    @Nested
    inner class GetManyByIdTests {
        @Test
        fun `tomt sett med ider - returnerer tom liste`() {
            navAnsattRepository.getManyById(emptySet()) shouldBe emptyList()
        }

        @Test
        fun `flere ider - returnerer flere ansatte`() {
            // Arrange
            val ansatteInTest = List(3) { lagNavAnsatt(navEnhetId = navEnhet.id) }
            ansatteInTest.forEach { navAnsattRepository.upsert(it) }

            // Act
            val faktiskeAnsatte = navAnsattRepository.getManyById(ansatteInTest.map { it.id }.toSet())

            // Assert
            faktiskeAnsatte shouldBe ansatteInTest
        }
    }

    @Nested
    inner class GetManyByNavIdentTests {
        @Test
        fun `tomt sett med Nav-identer - returnerer tom liste`() {
            navAnsattRepository.getManyByNavIdent(emptySet()) shouldBe emptyList()
        }

        @Test
        fun `flere Nav-identer - returnerer flere ansatte`() {
            // Arrange
            val ansatteInTest = List(3) { lagNavAnsatt(navEnhetId = navEnhet.id) }
            ansatteInTest.forEach { navAnsattRepository.upsert(it) }

            // Act
            val faktiskeAnsatte = navAnsattRepository.getManyByNavIdent(ansatteInTest.map { it.navIdent }.toSet())

            // Assert
            faktiskeAnsatte shouldBe ansatteInTest
        }
    }

    @Test
    fun `slettNavAnsatt - navansatt blir slettet`() {
        val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)
        navAnsattRepository.upsert(navAnsatt)

        navAnsattRepository.delete(navAnsatt.id)

        navAnsattRepository.get(navAnsatt.id) shouldBe null
    }
}
