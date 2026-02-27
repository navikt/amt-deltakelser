package no.nav.amt.deltaker.vurdering

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class VurderingRepositoryTest {
    private val vurderingRepository = VurderingRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `upsert - ny vurdering - inserter`() {
        val deltaker = TestData.lagDeltaker()
        val vurdering = TestData.lagVurdering(deltakerId = deltaker.id)
        TestRepository.insert(deltaker)

        vurderingRepository.upsert(vurdering)

        vurderingRepository.getForDeltaker(vurdering.deltakerId).size shouldBe 1
    }
}
