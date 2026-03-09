package no.nav.amt.lib.models.arrangor.melding

import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringAarsak {
    data object Syk : EndringAarsak

    data object FattJobb : EndringAarsak

    data object TrengerAnnenStotte : EndringAarsak

    data object Utdanning : EndringAarsak

    data object IkkeMott : EndringAarsak

    data class Annet(
        val beskrivelse: String,
    ) : EndringAarsak
}
