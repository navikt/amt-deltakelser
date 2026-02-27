package no.nav.amt.lib.models.deltaker.internalapis.deltaker.request

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.deltaker.DeltakerEndring

/*
    Endringsrequest er en dto for å kommunisere alle endringer som kan gjøres på en deltaker
    fra amt-deltaker-bff til amt-deltaker
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringRequest {
    val endretAv: String
    val endretAvEnhet: String

    fun toEndring(): DeltakerEndring.Endring
}
