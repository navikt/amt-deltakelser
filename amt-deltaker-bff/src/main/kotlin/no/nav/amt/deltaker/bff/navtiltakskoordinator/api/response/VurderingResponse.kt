package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype

data class VurderingResponse(
    val type: Vurderingstype,
    val begrunnelse: String?,
)
