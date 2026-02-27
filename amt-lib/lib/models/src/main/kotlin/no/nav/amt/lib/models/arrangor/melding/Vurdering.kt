package no.nav.amt.lib.models.arrangor.melding

import java.time.LocalDateTime
import java.util.UUID

data class Vurdering(
    override val id: UUID,
    override val deltakerId: UUID,
    override val opprettetAvArrangorAnsattId: UUID,
    override val opprettet: LocalDateTime,
    val vurderingstype: Vurderingstype,
    val begrunnelse: String?,
) : Melding
