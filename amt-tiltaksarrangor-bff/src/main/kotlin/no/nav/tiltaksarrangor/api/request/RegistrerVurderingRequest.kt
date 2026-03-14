package no.nav.tiltaksarrangor.api.request

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype

data class RegistrerVurderingRequest(
	val vurderingstype: Vurderingstype,
	val begrunnelse: String?,
)
