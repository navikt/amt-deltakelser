package no.nav.amt.deltaker.bff.veileder.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringFraArrangorEndringResponse {
    data class LeggTilOppstartsdato(
        val startdato: LocalDate,
        val sluttdato: LocalDate?,
    ) : EndringFraArrangorEndringResponse {
        constructor(model: EndringFraArrangor.LeggTilOppstartsdato) : this(
            startdato = model.startdato,
            sluttdato = model.sluttdato,
        )
    }

    companion object {
        fun fromModel(model: EndringFraArrangor.Endring) = when (model) {
            is EndringFraArrangor.LeggTilOppstartsdato -> LeggTilOppstartsdato(model)
        }
    }
}
