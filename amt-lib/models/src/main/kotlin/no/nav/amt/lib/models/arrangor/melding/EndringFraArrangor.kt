package no.nav.amt.lib.models.arrangor.melding

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class EndringFraArrangor(
    override val id: UUID,
    override val deltakerId: UUID,
    override val opprettetAvArrangorAnsattId: UUID,
    override val opprettet: LocalDateTime,
    val endring: Endring,
) : Melding {
    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    sealed interface Endring

    data class LeggTilOppstartsdato(
        val startdato: LocalDate,
        val sluttdato: LocalDate?,
    ) : Endring
}
