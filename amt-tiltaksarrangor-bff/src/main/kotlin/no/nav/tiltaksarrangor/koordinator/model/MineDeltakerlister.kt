package no.nav.tiltaksarrangor.koordinator.model

import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate
import java.util.UUID

data class MineDeltakerlister(
	val veilederFor: VeilederFor?,
	val koordinatorFor: KoordinatorFor?,
)

data class VeilederFor(
	val veilederFor: Int,
	val medveilederFor: Int,
)

data class KoordinatorFor(
	val deltakerlister: List<Deltakerliste>,
) {
	data class Deltakerliste(
		val id: UUID,
		val type: String,
		val navn: String,
		val startdato: LocalDate?,
		val sluttdato: LocalDate?,
		val oppstartstype: Oppstartstype,
	)
}
