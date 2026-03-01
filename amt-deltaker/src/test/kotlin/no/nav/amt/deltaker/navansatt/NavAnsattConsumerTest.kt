package no.nav.amt.deltaker.navansatt

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavAnsattConsumerTest {
    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()
    private val amtPersonServiceClient = mockk<AmtPersonServiceClient>()
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), amtPersonServiceClient)
    private val navAnsattConsumer = NavAnsattConsumer(
        navAnsattRepository,
        NavAnsattService(navAnsattRepository, amtPersonServiceClient, navEnhetService),
    )

    private val navEnhet = TestData.lagNavEnhet()
    private val navAnsatt = TestData.lagNavAnsatt(navEnhetId = navEnhet.id)

    companion object {
        @RegisterExtension
        private val dbExtension = DatabaseTestExtension()

        private fun NavAnsatt.toDto() = NavAnsattDto(id, navIdent, navn, epost, telefon, navEnhetId)
    }

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(navEnhet)
        navAnsattRepository.upsert(navAnsatt)
    }

    @Test
    fun `consumeNavAnsatt - ny navansatt - upserter`() = runTest {
        val navAnsatt = TestData.lagNavAnsatt()
        coEvery { amtPersonServiceClient.hentNavEnhet(navAnsatt.navEnhetId!!) } returns TestData.lagNavEnhet(navAnsatt.navEnhetId!!)

        navAnsattConsumer.consume(navAnsatt.id, objectMapper.writeValueAsString(navAnsatt.toDto()))

        navAnsattRepository.get(navAnsatt.id) shouldBe navAnsatt
    }

    @Test
    fun `consumeNavAnsatt - oppdatert navansatt - upserter`() = runTest {
        val oppdatertNavAnsatt = navAnsatt.copy(navn = "Nytt Navn")

        navAnsattConsumer.consume(navAnsatt.id, objectMapper.writeValueAsString(oppdatertNavAnsatt.toDto()))

        navAnsattRepository.get(navAnsatt.id) shouldBe oppdatertNavAnsatt
    }

    @Test
    fun `consumeNavAnsatt - tombstonet navansatt - sletter`() = runTest {
        navAnsattConsumer.consume(navAnsatt.id, null)

        navAnsattRepository.get(navAnsatt.id) shouldBe null
    }
}
