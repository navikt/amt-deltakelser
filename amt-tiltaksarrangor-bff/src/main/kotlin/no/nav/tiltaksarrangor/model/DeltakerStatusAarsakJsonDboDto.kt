package no.nav.tiltaksarrangor.model

import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class DeltakerStatusAarsakJsonDboDto(
	val type: DeltakerStatus.Aarsak.Type,
	val beskrivelse: String?,
)
