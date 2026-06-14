package no.nav.tiltaksarrangor.client.amtarrangor.dto

import java.util.UUID

data class OppdaterVeiledereForDeltakerRequest(
    val arrangorId: UUID,
    val veilederSomLeggesTil: List<VeilederAnsatt>,
    val veilederSomFjernes: List<VeilederAnsatt>,
)
