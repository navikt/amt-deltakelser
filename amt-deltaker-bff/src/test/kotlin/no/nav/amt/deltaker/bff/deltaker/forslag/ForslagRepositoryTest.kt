package no.nav.amt.deltaker.bff.deltaker.forslag

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestRepository
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
            sut.getForDeltakere(emptyList()).shouldBeEmpty()
        }

        @Test
        fun `tom database - returnerer tom liste`() {
            sut.getForDeltakere(listOf(UUID.randomUUID())).shouldBeEmpty()
        }

        @Test
        fun `henter forslag`() {
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)

            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            sut.upsert(forslag)

            val forslagFraDb = sut.getForDeltakere(listOf(deltaker.id))
            forslagFraDb.size shouldBe 1
            forslagFraDb.first().copy(opprettet = forslag.opprettet) shouldBe forslag
        }
    }
}
