package no.nav.amt.deltaker.bff.veileder.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringFraTiltakskoordinatorEndringResponse {
    data object DelMedArrangor : EndringFraTiltakskoordinatorEndringResponse

    data object SettPaaVenteliste : EndringFraTiltakskoordinatorEndringResponse

    data object TildelPlass : EndringFraTiltakskoordinatorEndringResponse

    data class Avslag(
        val aarsak: Aarsak,
        val begrunnelse: String?,
    ) : EndringFraTiltakskoordinatorEndringResponse {
        data class Aarsak(
            val type: EndringFraTiltakskoordinator.Avslag.Aarsak.Type,
            val beskrivelse: String? = null,
        ) {
            constructor(model: EndringFraTiltakskoordinator.Avslag.Aarsak) : this(
                type = model.type,
                beskrivelse = model.beskrivelse,
            )
        }

        constructor(model: EndringFraTiltakskoordinator.Avslag) : this(
            aarsak = Aarsak(model.aarsak),
            begrunnelse = model.begrunnelse,
        )
    }

    companion object {
        fun fromModel(model: EndringFraTiltakskoordinator.Endring): EndringFraTiltakskoordinatorEndringResponse = when (model) {
            EndringFraTiltakskoordinator.DelMedArrangor -> DelMedArrangor
            EndringFraTiltakskoordinator.SettPaaVenteliste -> SettPaaVenteliste
            EndringFraTiltakskoordinator.TildelPlass -> TildelPlass
            is EndringFraTiltakskoordinator.Avslag -> Avslag(model)
        }
    }
}
