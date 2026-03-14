package no.nav.tiltaksarrangor.repositories.model

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.util.UUID

data class TiltakstypeDbo(
	val id: UUID,
	val navn: String,
	val tiltakskode: Tiltakskode,
)
