package no.nav.tiltaksarrangor.client.amtperson

import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import java.util.UUID

data class NavEnhetDto(
	val id: UUID,
	val enhetId: String,
	val navn: String,
) {
	fun toNavEnhet(): NavEnhet = NavEnhet(
		id = id,
		enhetsnummer = enhetId,
		navn = navn,
	)
}
