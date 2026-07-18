package no.nav.amt.deltaker.bff.tiltaksarrangor.forslag

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class ForslagRepositoryTest {
    private val sut = ForslagRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class UpsertTests {
        @Test
        fun `upsert - nytt forslag - lagrer`() {
            val deltaker = TestData.lagDeltakerOld()
            TestRepository.insert(deltaker)

            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            sut.upsert(forslag)

            val forslagFraDb = TestRepository.getForslagForDeltaker(deltaker.id)
            forslagFraDb.size shouldBe 1
            forslagFraDb.first().id shouldBe forslag.id
            forslagFraDb.first().deltakerId shouldBe forslag.deltakerId
            forslagFraDb.first().begrunnelse shouldBe forslag.begrunnelse
        }

        @Test
        fun `upsert - eksisterende forslag - oppdaterer`() {
            val deltaker = TestData.lagDeltakerOld()
            TestRepository.insert(deltaker)

            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            sut.upsert(forslag)

            val oppdatertForslag = forslag.copy(begrunnelse = "Oppdatert begrunnelse")
            sut.upsert(oppdatertForslag)

            val forslagFraDb = TestRepository.getForslagForDeltaker(deltaker.id)
            forslagFraDb.size shouldBe 1
            forslagFraDb.first().begrunnelse shouldBe "Oppdatert begrunnelse"
        }
    }

    @Nested
    inner class DeleteTests {
        @Test
        fun `delete - forslag finnes - sletter`() {
            val deltaker = TestData.lagDeltakerOld()
            TestRepository.insert(deltaker)

            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            sut.upsert(forslag)

            sut.delete(forslag.id)

            TestRepository.getForslagForDeltaker(deltaker.id).shouldBeEmpty()
        }

        @Test
        fun `delete - forslag finnes ikke - feiler ikke`() {
            sut.delete(UUID.randomUUID())
        }
    }

    @Nested
    inner class DeleteForDeltakerTests {
        @Test
        fun `deleteForDeltaker - flere forslag - sletter alle for deltaker`() {
            val deltaker = TestData.lagDeltakerOld()
            TestRepository.insert(deltaker)

            sut.upsert(TestData.lagForslag(deltakerId = deltaker.id))
            sut.upsert(TestData.lagForslag(deltakerId = deltaker.id))

            sut.deleteForDeltaker(deltaker.id)

            TestRepository.getForslagForDeltaker(deltaker.id).shouldBeEmpty()
        }

        @Test
        fun `deleteForDeltaker - sletter ikke forslag for andre deltakere`() {
            val deltaker1 = TestData.lagDeltakerOld()
            val deltaker2 = TestData.lagDeltakerOld()
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)

            sut.upsert(TestData.lagForslag(deltakerId = deltaker1.id))
            sut.upsert(TestData.lagForslag(deltakerId = deltaker2.id))

            sut.deleteForDeltaker(deltaker1.id)

            TestRepository.getForslagForDeltaker(deltaker1.id).shouldBeEmpty()
            TestRepository.getForslagForDeltaker(deltaker2.id).size shouldBe 1
        }
    }

    @Nested
    inner class KanLagresTests {
        @Test
        fun `kanLagres - deltaker finnes - returnerer true`() {
            val deltaker = TestData.lagDeltakerOld()
            TestRepository.insert(deltaker)

            sut.kanLagres(deltaker.id) shouldBe true
        }

        @Test
        fun `kanLagres - deltaker finnes ikke - returnerer false`() {
            sut.kanLagres(UUID.randomUUID()) shouldBe false
        }
    }
}
