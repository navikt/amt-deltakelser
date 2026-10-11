package no.nav.amt.deltaker.bff.veileder.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringAarsakResponse {
    data object Syk : EndringAarsakResponse

    data object FattJobb : EndringAarsakResponse

    data object TrengerAnnenStotte : EndringAarsakResponse

    data object Utdanning : EndringAarsakResponse

    data object IkkeMott : EndringAarsakResponse

    data class Annet(
        val beskrivelse: String,
    ) : EndringAarsakResponse {
        constructor(model: EndringAarsak.Annet) : this(
            beskrivelse = model.beskrivelse,
        )
    }

    companion object {
        fun fromModel(model: EndringAarsak): EndringAarsakResponse = when (model) {
            EndringAarsak.Syk -> Syk
            EndringAarsak.FattJobb -> FattJobb
            EndringAarsak.TrengerAnnenStotte -> TrengerAnnenStotte
            EndringAarsak.Utdanning -> Utdanning
            EndringAarsak.IkkeMott -> IkkeMott
            is EndringAarsak.Annet -> Annet(model)
        }
    }
}
