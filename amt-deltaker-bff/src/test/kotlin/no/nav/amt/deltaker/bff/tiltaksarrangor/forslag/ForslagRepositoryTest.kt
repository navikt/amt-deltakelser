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
    inner class GetForDeltakereTests {
        @Test
        fun `tom id-liste - returnerer tom liste`() {
            TestRepository.getForslagForDeltakere(emptyList()).shouldBeEmpty()
        }

        @Test
        fun `tom database - returnerer tom liste`() {
            TestRepository.getForslagForDeltakere(listOf(UUID.randomUUID())).shouldBeEmpty()
        }

        @Test
        fun `henter forslag`() {
            val deltaker = TestData.lagDeltakerOld()
            TestRepository.insert(deltaker)

            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            sut.upsert(forslag)

            val forslagFraDb = TestRepository.getForslagForDeltakere(listOf(deltaker.id))
            forslagFraDb.size shouldBe 1
            forslagFraDb.first().copy(opprettet = forslag.opprettet) shouldBe forslag.copy()
        }
    }
}
