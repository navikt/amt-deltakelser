package no.nav.amt.deltaker.bff.veileder.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.Forslag
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface ForslagEndringResponse {
    data class ForlengDeltakelse(
        val sluttdato: LocalDate,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.ForlengDeltakelse) : this(
            sluttdato = model.sluttdato,
        )
    }

    data class AvsluttDeltakelse(
        val sluttdato: LocalDate?,
        val aarsak: EndringAarsakResponse?,
        val harDeltatt: Boolean?,
        val harFullfort: Boolean?,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.AvsluttDeltakelse) : this(
            sluttdato = model.sluttdato,
            aarsak = model.aarsak?.let(EndringAarsakResponse::fromModel),
            harDeltatt = model.harDeltatt,
            harFullfort = model.harFullfort,
        )
    }

    data class EndreAvslutning(
        val aarsak: EndringAarsakResponse?,
        val harDeltatt: Boolean?,
        val harFullfort: Boolean?,
        val sluttdato: LocalDate? = null,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.EndreAvslutning) : this(
            aarsak = model.aarsak?.let(EndringAarsakResponse::fromModel),
            harDeltatt = model.harDeltatt,
            harFullfort = model.harFullfort,
            sluttdato = model.sluttdato,
        )
    }

    data class IkkeAktuell(
        val aarsak: EndringAarsakResponse,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.IkkeAktuell) : this(
            aarsak = EndringAarsakResponse.fromModel(model.aarsak),
        )
    }

    data class Deltakelsesmengde(
        val deltakelsesprosent: Int,
        val dagerPerUke: Int?,
        val gyldigFra: LocalDate?,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.Deltakelsesmengde) : this(
            deltakelsesprosent = model.deltakelsesprosent,
            dagerPerUke = model.dagerPerUke,
            gyldigFra = model.gyldigFra,
        )
    }

    data class Startdato(
        val startdato: LocalDate,
        val sluttdato: LocalDate?,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.Startdato) : this(
            startdato = model.startdato,
            sluttdato = model.sluttdato,
        )
    }

    data class Sluttdato(
        val sluttdato: LocalDate,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.Sluttdato) : this(
            sluttdato = model.sluttdato,
        )
    }

    data class Sluttarsak(
        val aarsak: EndringAarsakResponse,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.Sluttarsak) : this(
            aarsak = EndringAarsakResponse.fromModel(model.aarsak),
        )
    }

    data object FjernOppstartsdato : ForslagEndringResponse

    companion object {
        fun fromModel(model: Forslag.Endring): ForslagEndringResponse = when (model) {
            is Forslag.ForlengDeltakelse -> ForlengDeltakelse(model)
            is Forslag.AvsluttDeltakelse -> AvsluttDeltakelse(model)
            is Forslag.EndreAvslutning -> EndreAvslutning(model)
            is Forslag.IkkeAktuell -> IkkeAktuell(model)
            is Forslag.Deltakelsesmengde -> Deltakelsesmengde(model)
            is Forslag.Startdato -> Startdato(model)
            is Forslag.Sluttdato -> Sluttdato(model)
            is Forslag.Sluttarsak -> Sluttarsak(model)
            Forslag.FjernOppstartsdato -> FjernOppstartsdato
        }
    }
}
