package no.nav.amt.deltaker.bff.arrangor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ArrangorConsumerTest {
    private val arrangorRepository = ArrangorRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `consumeArrangor - ny arrangor - upserter`() {
        val arrangor = lagArrangor()
        val arrangorConsumer = ArrangorConsumer(arrangorRepository)

        runBlocking {
            arrangorConsumer.consume(arrangor.id, objectMapper.writeValueAsString(arrangor))
        }

        arrangorRepository.get(arrangor.id) shouldBe arrangor
    }

    @Test
    fun `consumeArrangor - oppdatert arrangor - upserter`() {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        val oppdatertArrangor = arrangor.copy(navn = "Oppdatert Arrangor")

        val arrangorConsumer = ArrangorConsumer(arrangorRepository)

        runBlocking {
            arrangorConsumer.consume(arrangor.id, objectMapper.writeValueAsString(oppdatertArrangor))
        }

        arrangorRepository.get(arrangor.id) shouldBe oppdatertArrangor
    }

    @Test
    fun `consumeArrangor - tombstonet arrangor - sletter`() {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        val arrangorConsumer = ArrangorConsumer(arrangorRepository)

        runBlocking {
            arrangorConsumer.consume(arrangor.id, null)
        }

        arrangorRepository.get(arrangor.id) shouldBe null
    }
}
