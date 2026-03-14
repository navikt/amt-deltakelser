package no.nav.tiltaksarrangor.client.amtarrangor.dto

import no.nav.tiltaksarrangor.model.Veiledertype
import java.util.UUID

data class OppdaterVeiledereForDeltakerRequest(
	val arrangorId: UUID,
	val veilederSomLeggesTil: List<VeilederAnsatt>,
	val veilederSomFjernes: List<VeilederAnsatt>,
)

data class VeilederAnsatt(
	val ansattId: UUID,
	val type: Veiledertype,
)
