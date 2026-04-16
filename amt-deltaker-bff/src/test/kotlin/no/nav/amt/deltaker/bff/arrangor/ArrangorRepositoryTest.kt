package no.nav.amt.deltaker.bff.arrangor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ArrangorRepositoryTest {
    private val arrangorRepository = ArrangorRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `upsert - ny arrangor - inserter`() = runTest {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        arrangorRepository.get(arrangor.id) shouldBe arrangor
    }

    @Test
    fun `upsert - eksisterende arrangor - oppdaterer`() = runTest {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        val oppdatertArrangor = arrangor.copy(navn = "Oppdatert Arrangor")
        arrangorRepository.upsert(oppdatertArrangor)

        arrangorRepository.get(arrangor.id) shouldBe oppdatertArrangor
    }

    @Test
    fun `delete - eksisterende arrangor - sletter`() = runTest {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        arrangorRepository.delete(arrangor.id)

        arrangorRepository.get(arrangor.id) shouldBe null
    }
}
