package no.nav.tiltaksarrangor.koordinator.model

import no.nav.tiltaksarrangor.model.Veiledertype
import java.util.UUID

data class LeggTilVeiledereRequest(
	val veiledere: List<VeilederRequest>,
)

data class VeilederRequest(
	val ansattId: UUID,
	val erMedveileder: Boolean,
) {
	fun toVeiledertype(): Veiledertype {
		if (erMedveileder) {
			return Veiledertype.MEDVEILEDER
		} else {
			return Veiledertype.VEILEDER
		}
	}
}
