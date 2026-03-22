package no.nav.amt.deltaker.bff.veileder.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDateTime

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface ForslagResponseStatus {
    data object VenterPaSvar : ForslagResponseStatus

    data class Godkjent(
        val godkjent: LocalDateTime,
    ) : ForslagResponseStatus

    data class Avvist(
        val avvistAv: String,
        val avvistAvEnhet: String,
        val avvist: LocalDateTime,
        val begrunnelseFraNav: String,
    ) : ForslagResponseStatus

    data class Tilbakekalt(
        val tilbakekalt: LocalDateTime,
    ) : ForslagResponseStatus

    data class Erstattet(
        val erstattet: LocalDateTime,
    ) : ForslagResponseStatus
}
