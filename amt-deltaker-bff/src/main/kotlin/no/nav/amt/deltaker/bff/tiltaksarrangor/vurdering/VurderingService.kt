package no.nav.amt.deltaker.bff.deltaker.vurdering

import no.nav.amt.lib.models.arrangor.melding.Vurdering
import java.util.UUID

class VurderingService(
    private val vurderingRepository: VurderingRepository,
) {
    fun getSisteVurderingForDeltaker(deltakerId: UUID) = vurderingRepository.getForDeltaker(deltakerId).maxByOrNull { it.opprettet }

    fun upsertMany(vurderinger: List<Vurdering>) = vurderinger.forEach { vurdering -> vurderingRepository.upsert(vurdering) }
}
