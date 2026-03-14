package no.nav.tiltaksarrangor.repositories.model

import java.util.UUID

data class ArrangorDbo(
	val id: UUID,
	val navn: String,
	val organisasjonsnummer: String,
	val overordnetArrangorId: UUID?,
)
