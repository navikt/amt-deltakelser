package no.nav.amt.deltaker.bff.deltaker.vurdering

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagVurdering
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
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
    fun `getForDeltaker - vurdering finnes - returnerer vurdering`() {
        val deltaker = lagDeltaker()
        val vurdering = lagVurdering(deltakerId = deltaker.id)

        TestRepository.insert(deltaker)

        vurderingRepository.upsert(vurdering)
        val upsertedVurdering = vurderingRepository.getForDeltaker(deltaker.id)

        upsertedVurdering.size shouldBe 1
        upsertedVurdering[0].deltakerId shouldBe vurdering.deltakerId
        upsertedVurdering[0].opprettetAvArrangorAnsattId shouldBe vurdering.opprettetAvArrangorAnsattId
        upsertedVurdering[0].begrunnelse shouldBe vurdering.begrunnelse
        upsertedVurdering[0].vurderingstype shouldBe vurdering.vurderingstype
    }

    @Test
    fun `getForDeltaker - vurderinger finnes - returnerer alle vurdering`() {
        val deltaker1 = lagDeltaker()
        val deltaker2 = lagDeltaker()
        val vurdering1 = lagVurdering(deltakerId = deltaker1.id, vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE)
        val vurdering2 = lagVurdering(deltakerId = deltaker1.id, vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE)
        val vurdering3 = lagVurdering(deltakerId = deltaker2.id)

        TestRepository.insert(deltaker1)
        TestRepository.insert(deltaker2)

        vurderingRepository.upsert(vurdering1)
        vurderingRepository.upsert(vurdering2)
        vurderingRepository.upsert(vurdering3)

        val upsertedVurderinger = vurderingRepository.getForDeltaker(deltaker1.id)

        upsertedVurderinger.size shouldBe 2
    }
}
