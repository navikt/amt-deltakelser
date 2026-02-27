package no.nav.amt.lib.models.deltaker

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import java.time.LocalDateTime
import java.util.*

class VurderingFraArrangorData (
    val id: UUID,
    val deltakerId: UUID,
    val vurderingstype: Vurderingstype,
    val begrunnelse: String?,
    val opprettetAvArrangorAnsattId: UUID,
    val opprettet: LocalDateTime,
)