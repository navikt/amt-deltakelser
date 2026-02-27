package no.nav.amt.lib.models.arrangor.melding

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDateTime
import java.util.UUID
/*
    Datastruktur for arrangor-melding-v1 topic
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface Melding {
    val id: UUID
    val deltakerId: UUID
    val opprettetAvArrangorAnsattId: UUID
    val opprettet: LocalDateTime
}
