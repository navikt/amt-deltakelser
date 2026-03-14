package no.nav.tiltaksarrangor.model

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import java.time.LocalDateTime

data class Vurdering(
	val vurderingstype: Vurderingstype,
	val begrunnelse: String?,
	val gyldigFra: LocalDateTime,
	val gyldigTil: LocalDateTime?,
)
