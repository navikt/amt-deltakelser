package no.nav.amt.deltaker.bff.navansatt

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.dto.NavAnsattDto
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavAnsattConsumerTest {
    private val amtPersonServiceClient = mockk<AmtPersonServiceClient>()
    private val navAnsattRepository = NavAnsattRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `consumeNavAnsatt - ny navansatt - upserter`() = runTest {
        val navAnsatt = lagNavAnsatt()
        val navAnsattConsumer = NavAnsattConsumer(NavAnsattService(navAnsattRepository, amtPersonServiceClient))

        navAnsattConsumer.consume(navAnsatt.id, objectMapper.writeValueAsString(navAnsatt.toDto()))

        navAnsattRepository.get(navAnsatt.id) shouldBe navAnsatt
    }

    @Test
    fun `consumeNavAnsatt - oppdatert navansatt - upserter`() = runTest {
        val navAnsatt = lagNavAnsatt()
        navAnsattRepository.upsert(navAnsatt)
        val oppdatertNavAnsatt = navAnsatt.copy(navn = "Nytt Navn")
        val navAnsattConsumer = NavAnsattConsumer(NavAnsattService(navAnsattRepository, amtPersonServiceClient))

        navAnsattConsumer.consume(navAnsatt.id, objectMapper.writeValueAsString(oppdatertNavAnsatt.toDto()))

        navAnsattRepository.get(navAnsatt.id) shouldBe oppdatertNavAnsatt
    }

    @Test
    fun `consumeNavAnsatt - tombstonet navansatt - sletter`() = runTest {
        val navAnsatt = lagNavAnsatt()
        navAnsattRepository.upsert(navAnsatt)
        val navAnsattConsumer = NavAnsattConsumer(NavAnsattService(navAnsattRepository, amtPersonServiceClient))

        navAnsattConsumer.consume(navAnsatt.id, null)

        navAnsattRepository.get(navAnsatt.id) shouldBe null
    }
}

private fun NavAnsatt.toDto() = NavAnsattDto(id, navident = navIdent, navn = navn, epost = epost, telefon = telefon, null)
