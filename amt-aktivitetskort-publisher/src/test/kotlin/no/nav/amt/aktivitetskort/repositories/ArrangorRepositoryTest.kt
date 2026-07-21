package no.nav.amt.aktivitetskort.repositories

import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.aktivitetskort.utils.RepositoryResult
import org.junit.jupiter.api.Test
import java.util.UUID

class ArrangorRepositoryTest(
    private val arrangorRepository: ArrangorRepository,
) : RepositoryTestBase() {
    @Test
    fun `get(uuid) - finnes - returnerer arrangor`() {
        val arrangor = TestData.lagArrangor()
        testDatabase.insertArrangor(arrangor)
        arrangorRepository.get(arrangor.id) shouldBe arrangor
    }

    @Test
    fun `get(uuid) - finnes ikke - returnerer null`() {
        arrangorRepository.get(UUID.randomUUID()) shouldBe null
    }

    @Test
    fun `get(orgnr) - finnes - returnerer arrangor`() {
        val arrangor = TestData.lagArrangor()
        testDatabase.insertArrangor(arrangor)
        arrangorRepository.get(arrangor.organisasjonsnummer) shouldBe arrangor
    }

    @Test
    fun `get(orgnr) - finnes ikke - returnerer null`() {
        arrangorRepository.get("foobar") shouldBe null
    }

    @Test
    fun `upsert - finnes ikke - returnerer Created Result - finnes in database`() {
        val arrangor = TestData.lagArrangor()
        when (val result = arrangorRepository.upsert(arrangor)) {
            is RepositoryResult.Created -> result.data shouldBe arrangor
            else -> fail("Should be Created, was $result")
        }

        arrangorRepository.get(arrangor.id) shouldBe arrangor
    }

    @Test
    fun `upsert - finnes - returnerer NoChange Result`() {
        val arrangor = TestData
            .lagArrangor()
            .also { arrangorRepository.upsert(it) }

        when (val result = arrangorRepository.upsert(arrangor)) {
            is RepositoryResult.Created -> {
                fail("Should be NoChange, was $result")
            }

            is RepositoryResult.Modified -> {
                fail("Should be NoChange, was $result")
            }

            is RepositoryResult.NoChange -> {}
        }
    }

    @Test
    fun `upsert - endret - returnerer Modified Result og oppdaterer database`() {
        val initialArrangor = TestData
            .lagArrangor()
            .also { arrangorRepository.upsert(it) }

        val updatedArrangor = initialArrangor.copy(
            navn = "UPDATED",
        )

        when (val result = arrangorRepository.upsert(updatedArrangor)) {
            is RepositoryResult.Modified -> result.data shouldBe updatedArrangor
            else -> fail("Should be Modified, was $result")
        }

        arrangorRepository.get(initialArrangor.id) shouldBe updatedArrangor
    }
}
