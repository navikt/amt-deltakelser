package no.nav.amt.deltaker.bff.deltaker.navbruker

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.testing.DatabaseTestExtension
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
        val navBrukerInTest = TestData.lagNavBruker()
        navAnsattRepository.upsert(TestData.lagNavAnsatt(navBrukerInTest.navVeilederId!!))
        navEnhetRepository.upsert(TestData.lagNavEnhet(navBrukerInTest.navEnhetId!!))

        navBrukerRepository.upsert(navBrukerInTest).getOrNull() shouldBe navBrukerInTest
    }

    @Test
    fun `upsert - oppdatert bruker - oppdaterer`() {
        val navBrukerInTest = TestData.lagNavBruker()
        TestRepository.insert(navBrukerInTest)

        val oppdatertBruker = navBrukerInTest.copy(
            personident = TestData.randomIdent(),
            fornavn = "Nytt Fornavn",
            mellomnavn = null,
            etternavn = "Nytt Etternavn",
            adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG,
        )

        navBrukerRepository.upsert(oppdatertBruker).getOrNull() shouldBe oppdatertBruker
        navBrukerRepository.get(navBrukerInTest.personId).getOrNull() shouldBe oppdatertBruker
    }
}
