package no.nav.tiltaksarrangor.koordinator.model

import java.util.UUID

data class TilgjengeligVeileder(
	val ansattId: UUID,
	val fornavn: String,
	val mellomnavn: String?,
	val etternavn: String,
)
