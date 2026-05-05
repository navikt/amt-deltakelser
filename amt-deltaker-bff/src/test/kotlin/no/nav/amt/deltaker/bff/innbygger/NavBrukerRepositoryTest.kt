package no.nav.amt.deltaker.bff.deltaker.navbruker

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavBrukerRepositoryTest {
    private val navAnsattRepository = NavAnsattRepository()
    private val navBrukerRepository = NavBrukerRepository()
    private val navEnhetRepository = NavEnhetRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `upsert - ny bruker - inserter`() {
        val navBrukerInTest = lagNavBruker()
        navAnsattRepository.upsert(lagNavAnsatt(navBrukerInTest.navVeilederId!!))
        navEnhetRepository.upsert(lagNavEnhet(navBrukerInTest.navEnhetId!!))

        navBrukerRepository.upsert(navBrukerInTest).getOrNull() shouldBe navBrukerInTest
    }

    @Test
    fun `upsert - oppdatert bruker - oppdaterer`() {
        val navBrukerInTest = lagNavBruker()
        TestRepository.insert(navBrukerInTest)

        val oppdatertBruker = navBrukerInTest.copy(
            personident = randomIdent(),
            fornavn = "Nytt Fornavn",
            mellomnavn = null,
            etternavn = "Nytt Etternavn",
            adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG,
        )

        navBrukerRepository.upsert(oppdatertBruker).getOrNull() shouldBe oppdatertBruker
        navBrukerRepository.get(navBrukerInTest.personId).getOrNull() shouldBe oppdatertBruker
    }
}
