package no.nav.tiltaksarrangor.consumer.model

import java.util.UUID

data class DeltakerNavVeilederDto(
	val id: UUID,
	val navn: String,
	val epost: String?,
	val telefonnummer: String?,
)
