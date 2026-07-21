package no.nav.amt.aktivitetskort.repositories

import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.database.TestData.lagArrangor
import no.nav.amt.aktivitetskort.database.TestData.lagDeltakerliste
import no.nav.amt.aktivitetskort.utils.RepositoryResult
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class DeltakerlisteRepositoryTest(
    private val deltakerlisteRepository: DeltakerlisteRepository,
) : RepositoryTestBase() {
    @Nested
    inner class Get {
        @Test
        fun `get - finnes ikke - returnerer null`() {
            deltakerlisteRepository.get(UUID.randomUUID()) shouldBe null
        }

        @Test
        fun `finnes - returnerer deltakerliste`() {
            val deltakerliste = lagDeltakerliste()
            testDatabase.insertArrangor(lagArrangor(deltakerliste.arrangorId))
            deltakerlisteRepository.upsert(deltakerliste)

            deltakerlisteRepository.get(deltakerliste.id) shouldBe deltakerliste
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `finnes ikke - kaster ingen feil`() {
            shouldNotThrowAny {
                deltakerlisteRepository.delete(UUID.randomUUID())
            }
        }

        @Test
        fun `finnes - sletter deltakerliste`() {
            val deltakerliste = lagDeltakerliste()
            testDatabase.insertArrangor(lagArrangor(deltakerliste.arrangorId))
            deltakerlisteRepository.upsert(deltakerliste)

            deltakerlisteRepository.get(deltakerliste.id) shouldBe deltakerliste

            deltakerlisteRepository.delete(deltakerliste.id)

            deltakerlisteRepository.get(deltakerliste.id) shouldBe null
        }
    }

    @Nested
    inner class Upsert {
        @Test
        fun `finnes ikke - returnerer Created Result - finnes in database`() {
            val deltakerliste = lagDeltakerliste()
            testDatabase.insertArrangor(lagArrangor(deltakerliste.arrangorId))
            when (val result = deltakerlisteRepository.upsert(deltakerliste)) {
                is RepositoryResult.Created -> result.data shouldBe deltakerliste
                else -> fail("Should be Created, was $result")
            }

            deltakerlisteRepository.get(deltakerliste.id) shouldBe deltakerliste
        }

        @Test
        fun `finnes - returnerer NoChange Result`() {
            val deltakerliste = lagDeltakerliste()
                .also { testDatabase.insertArrangor(lagArrangor(it.arrangorId)) }
                .also { deltakerlisteRepository.upsert(it) }

            when (val result = deltakerlisteRepository.upsert(deltakerliste)) {
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
        fun `endret - returnerer Modified Result og oppdaterer database`() {
            val initialDeltakerliste = lagDeltakerliste()
                .also { testDatabase.insertArrangor(lagArrangor(it.arrangorId)) }
                .also { deltakerlisteRepository.upsert(it) }

            val updatedDeltakerliste = initialDeltakerliste.copy(
                navn = "UPDATED",
            )

            when (val result = deltakerlisteRepository.upsert(updatedDeltakerliste)) {
                is RepositoryResult.Modified -> result.data shouldBe updatedDeltakerliste
                else -> fail("Should be Modified, was $result")
            }

            deltakerlisteRepository.get(initialDeltakerliste.id) shouldBe updatedDeltakerliste
        }

        @Test
        fun `endret arrangor - returnerer Modified Result og oppdaterer database`() {
            val initialDeltakerliste = lagDeltakerliste()
                .also { testDatabase.insertArrangor(lagArrangor(it.arrangorId)) }
                .also { deltakerlisteRepository.upsert(it) }
            val nyArrangor = lagArrangor()
                .also { testDatabase.insertArrangor(it) }

            val updatedDeltakerliste = initialDeltakerliste.copy(
                arrangorId = nyArrangor.id,
            )

            when (val result = deltakerlisteRepository.upsert(updatedDeltakerliste)) {
                is RepositoryResult.Modified -> result.data shouldBe updatedDeltakerliste
                else -> fail("Should be Modified, was $result")
            }

            deltakerlisteRepository.get(initialDeltakerliste.id) shouldBe updatedDeltakerliste
        }
    }
}
