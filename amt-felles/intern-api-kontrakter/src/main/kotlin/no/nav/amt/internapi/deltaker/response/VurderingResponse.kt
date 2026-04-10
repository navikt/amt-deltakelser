package no.nav.amt.internapi.deltaker.response

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Vurdering

data class VurderingResponse(
    val type: Vurderingstype,
    val begrunnelse: String?,
) {
    companion object {
        fun fromVurdering(vurdering: Vurdering) = with(vurdering) {
            VurderingResponse(
                type = vurderingstype,
                begrunnelse = begrunnelse,
            )
        }
    }
}
