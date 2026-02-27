package no.nav.amt.deltaker.bff.tiltakskoordinator.api.response

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype

data class VurderingResponse(
    val type: Vurderingstype,
    val begrunnelse: String?,
)
