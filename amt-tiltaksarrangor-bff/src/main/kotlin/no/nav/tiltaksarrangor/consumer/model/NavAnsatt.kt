package no.nav.tiltaksarrangor.consumer.model

import java.util.UUID

data class NavAnsatt(
	val id: UUID,
	val navident: String,
	val navn: String,
	val epost: String?,
	val telefon: String?,
)
