package no.nav.amt.deltaker.bff.tiltaksarrangor.vurdering

import no.nav.amt.lib.models.arrangor.melding.Vurdering

class VurderingService(
    private val vurderingRepository: VurderingRepository,
) {
    fun upsertMany(vurderinger: List<Vurdering>) = vurderinger.forEach { vurdering -> vurderingRepository.upsert(vurdering) }
}
