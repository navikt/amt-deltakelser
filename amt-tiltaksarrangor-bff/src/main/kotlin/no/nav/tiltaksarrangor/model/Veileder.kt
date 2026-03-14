package no.nav.tiltaksarrangor.model

import java.util.UUID

data class Veileder(
	val ansattId: UUID,
	val deltakerId: UUID,
	val veiledertype: Veiledertype,
	val fornavn: String,
	val mellomnavn: String?,
	val etternavn: String,
)
