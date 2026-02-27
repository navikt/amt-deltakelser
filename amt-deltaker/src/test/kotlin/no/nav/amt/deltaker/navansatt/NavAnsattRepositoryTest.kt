package no.nav.amt.deltaker.navansatt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.BeforeEach
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

    @Test
    fun `getMany - flere navidenter - returnerer flere ansatte`() {
        val ansatte = List(3) { lagNavAnsatt(navEnhetId = navEnhet.id) }
        ansatte.forEach { navAnsattRepository.upsert(it) }

        val faktiskeAnsatte = navAnsattRepository.getMany(ansatte.map { it.navIdent })

        faktiskeAnsatte.size shouldBe ansatte.size
        faktiskeAnsatte.find { it == ansatte[0] } shouldNotBe null
        faktiskeAnsatte.find { it == ansatte[1] } shouldNotBe null
        faktiskeAnsatte.find { it == ansatte[2] } shouldNotBe null
    }

    @Test
    fun `slettNavAnsatt - navansatt blir slettet`() {
        val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)
        navAnsattRepository.upsert(navAnsatt)

        navAnsattRepository.delete(navAnsatt.id)

        navAnsattRepository.get(navAnsatt.id) shouldBe null
    }
}
