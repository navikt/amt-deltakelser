package no.nav.tiltaksarrangor.consumer.model

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.repositories.model.TiltakstypeDbo
import java.util.UUID

data class TiltakstypePayload(
	val id: UUID,
	val navn: String,
	val tiltakskode: String,
) {
	fun toModel() = TiltakstypeDbo(
		id = id,
		navn = navn,
		tiltakskode = Tiltakskode.valueOf(tiltakskode),
	)
}
