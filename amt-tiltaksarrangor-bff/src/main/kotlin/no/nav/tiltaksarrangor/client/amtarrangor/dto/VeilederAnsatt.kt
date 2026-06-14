package no.nav.tiltaksarrangor.client.amtarrangor.dto

import no.nav.tiltaksarrangor.model.Veiledertype
import java.util.UUID

data class VeilederAnsatt(
    val ansattId: UUID,
    val type: Veiledertype,
)
