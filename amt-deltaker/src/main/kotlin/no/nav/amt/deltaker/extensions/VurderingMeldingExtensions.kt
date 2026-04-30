package no.nav.amt.deltaker.extensions

import no.nav.amt.lib.models.deltaker.Vurdering

fun no.nav.amt.lib.models.arrangor.melding.Vurdering.toVurdering() = Vurdering(
    id = id,
    deltakerId = deltakerId,
    vurderingstype = vurderingstype,
    begrunnelse = begrunnelse,
    opprettetAvArrangorAnsattId = opprettetAvArrangorAnsattId,
    gyldigFra = opprettet,
)
