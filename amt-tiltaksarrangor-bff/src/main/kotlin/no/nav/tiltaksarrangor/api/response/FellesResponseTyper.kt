package no.nav.tiltaksarrangor.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.tiltaksarrangor.model.DeltakerStatusAarsakJsonDboDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakelsesinnholdResponse(
    val ledetekst: String?,
    val innhold: List<InnholdResponse>,
) {
    constructor(model: Deltakelsesinnhold) : this(
        ledetekst = model.ledetekst,
        innhold = model.innhold.map(::InnholdResponse),
    )

    data class InnholdResponse(
        val tekst: String,
        val innholdskode: String,
        val valgt: Boolean,
        val beskrivelse: String?,
    ) {
        constructor(model: Innhold) : this(
            tekst = model.tekst,
            innholdskode = model.innholdskode,
            valgt = model.valgt,
            beskrivelse = model.beskrivelse,
        )
    }
}

data class DeltakerStatusResponse(
    val id: UUID,
    val type: DeltakerStatus.Type,
    val aarsak: Aarsak?,
    val gyldigFra: LocalDateTime,
    val gyldigTil: LocalDateTime?,
    val opprettet: LocalDateTime,
) {
    data class Aarsak(
        val type: DeltakerStatus.Aarsak.Type,
        val beskrivelse: String?,
    ) {
        constructor(model: DeltakerStatus.Aarsak) : this(
            type = model.type,
            beskrivelse = model.beskrivelse,
        )

        constructor(model: DeltakerStatusAarsakJsonDboDto) : this(
            type = model.type,
            beskrivelse = model.beskrivelse,
        )
    }

    constructor(model: DeltakerStatus) : this(
        id = model.id,
        type = model.type,
        aarsak = model.aarsak?.let(::Aarsak),
        gyldigFra = model.gyldigFra,
        gyldigTil = model.gyldigTil,
        opprettet = model.opprettet,
    )
}

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
        fun fromModel(model: EndringFraArrangor.Endring): EndringFraArrangorEndringResponse = when (model) {
            is EndringFraArrangor.LeggTilOppstartsdato -> LeggTilOppstartsdato(model)
        }
    }
}

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
