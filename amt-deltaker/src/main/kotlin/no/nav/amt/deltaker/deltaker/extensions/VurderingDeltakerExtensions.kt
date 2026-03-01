package no.nav.amt.deltaker.deltaker.extensions

import no.nav.amt.deltaker.deltaker.vurdering.Vurdering
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.VurderingFraArrangorData

fun Vurdering.toVurderingFraArrangorData() = VurderingFraArrangorData(
    id = id,
    deltakerId = deltakerId,
    vurderingstype = Vurderingstype.valueOf(vurderingstype.name),
    begrunnelse = begrunnelse,
    opprettetAvArrangorAnsattId = opprettetAvArrangorAnsattId,
    opprettet = gyldigFra,
)
