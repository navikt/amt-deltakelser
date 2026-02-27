package no.nav.amt.deltaker.deltaker.vurdering

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import java.time.LocalDateTime
import java.util.UUID

data class Vurdering(
    val id: UUID,
    val deltakerId: UUID,
    val vurderingstype: Vurderingstype,
    val begrunnelse: String?,
    val opprettetAvArrangorAnsattId: UUID,
    val gyldigFra: LocalDateTime,
)
