package no.nav.amt.deltaker.deltaker.vurdering

import java.util.UUID

class VurderingService(
    private val vurderingRepository: VurderingRepository,
) {
    fun getSisteForDeltaker(deltakerId: UUID) = vurderingRepository.getForDeltaker(deltakerId).maxByOrNull { it.gyldigFra }
}
