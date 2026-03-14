package no.nav.tiltaksarrangor.client.amtperson

import java.util.UUID

data class NavAnsattResponse(
	val id: UUID,
	val navIdent: String,
	val navn: String,
	val epost: String?,
	val telefon: String?,
)
