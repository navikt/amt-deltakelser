package no.nav.amt.deltaker.innbygger

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavBrukerRepositoryTest {
    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()
    private val navBrukerRepository = NavBrukerRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `upsert - ny bruker - inserter`() {
        val navEnhet = lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)

        val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)
        navAnsattRepository.upsert(navAnsatt)

        val bruker = lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)

        navBrukerRepository.upsert(bruker).getOrNull() shouldBe bruker
    }

    @Test
    fun `upsert - oppdatert bruker - oppdaterer`() {
        val bruker = lagNavBruker()
        TestRepository.insert(bruker)

        val oppdatertBruker = bruker.copy(
            personident = randomIdent(),
            fornavn = "Nytt Fornavn",
            mellomnavn = null,
            etternavn = "Nytt Etternavn",
        )
        navBrukerRepository.upsert(oppdatertBruker).getOrNull() shouldBe oppdatertBruker
        navBrukerRepository.get(bruker.personId).getOrNull() shouldBe oppdatertBruker
    }
}
