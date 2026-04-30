package no.nav.amt.deltaker.tiltaksarrangor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ArrangorConsumerTest {
    private val arrangorRepository = ArrangorRepository()
    val arrangorConsumer = ArrangorConsumer(arrangorRepository)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `consumeArrangor - ny arrangor - upserter`() = runTest {
        val arrangor = lagArrangor()

        arrangorConsumer.consume(arrangor.id, objectMapper.writeValueAsString(arrangor))

        arrangorRepository.get(arrangor.id) shouldBe arrangor
    }

    @Test
    fun `consumeArrangor - oppdatert arrangor - upserter`() = runTest {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        val oppdatertArrangor = arrangor.copy(navn = "Oppdatert Arrangor")

        arrangorConsumer.consume(arrangor.id, objectMapper.writeValueAsString(oppdatertArrangor))

        arrangorRepository.get(arrangor.id) shouldBe oppdatertArrangor
    }

    @Test
    fun `consumeArrangor - tombstonet arrangor - sletter`() = runTest {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        arrangorConsumer.consume(arrangor.id, null)

        arrangorRepository.get(arrangor.id) shouldBe null
    }
}
