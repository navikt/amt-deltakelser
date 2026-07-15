package no.nav.amt.deltaker.bff.veileder.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse.InnholdResponse
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed class DeltakerEndringEndringResponse {
    data class EndreBakgrunnsinformasjon(
        val bakgrunnsinformasjon: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndreBakgrunnsinformasjon) : this(
            bakgrunnsinformasjon = model.bakgrunnsinformasjon,
        )
    }

    data class EndreInnhold(
        val ledetekst: String?,
        val innhold: List<InnholdResponse>,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndreInnhold) : this(
            ledetekst = model.ledetekst,
            innhold = model.innhold.map(::InnholdResponse),
        )
    }

    data class EndreDeltakelsesmengde(
        val deltakelsesprosent: Float?,
        val dagerPerUke: Float?,
        val gyldigFra: LocalDate?,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndreDeltakelsesmengde) : this(
            deltakelsesprosent = model.deltakelsesprosent,
            dagerPerUke = model.dagerPerUke,
            gyldigFra = model.gyldigFra,
            begrunnelse = model.begrunnelse,
        )
    }

    data class EndreStartdato(
        val startdato: LocalDate?,
        val sluttdato: LocalDate?,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndreStartdato) : this(
            startdato = model.startdato,
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
        )
    }

    data class EndreSluttdato(
        val sluttdato: LocalDate,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndreSluttdato) : this(
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
        )
    }

    data class ForlengDeltakelse(
        val sluttdato: LocalDate,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.ForlengDeltakelse) : this(
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
        )
    }

    data class IkkeAktuell(
        val aarsak: AarsakResponse,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.IkkeAktuell) : this(
            aarsak = AarsakResponse(model.aarsak),
            begrunnelse = model.begrunnelse,
        )
    }

    data class AvsluttDeltakelse(
        val aarsak: AarsakResponse?,
        val sluttdato: LocalDate,
        val begrunnelse: String?,
        val harFullfort: Boolean,
        val oppstartstype: Oppstartstype?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.AvsluttDeltakelse, oppstartstype: Oppstartstype?) : this(
            aarsak = model.aarsak?.let(::AarsakResponse),
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
            harFullfort = true,
            oppstartstype = oppstartstype,
        )

        constructor(model: DeltakerEndring.Endring.AvbrytDeltakelse, oppstartstype: Oppstartstype?) : this(
            aarsak = AarsakResponse(model.aarsak),
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
            harFullfort = false,
            oppstartstype = oppstartstype,
        )
    }

    data class EndreAvslutning(
        val aarsak: AarsakResponse?,
        val sluttdato: LocalDate?,
        val begrunnelse: String?,
        val harFullfort: Boolean?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndreAvslutning) : this(
            aarsak = model.aarsak?.let(::AarsakResponse),
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
            harFullfort = model.harFullfort,
        )
    }

    data class EndreSluttarsak(
        val aarsak: AarsakResponse,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndreSluttarsak) : this(
            aarsak = AarsakResponse(model.aarsak),
            begrunnelse = model.begrunnelse,
        )
    }

    data class ReaktiverDeltakelse(
        val reaktivertDato: LocalDate,
        val begrunnelse: String,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.ReaktiverDeltakelse) : this(
            reaktivertDato = model.reaktivertDato,
            begrunnelse = model.begrunnelse,
        )
    }

    data class FjernOppstartsdato(
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.FjernOppstartsdato) : this(
            begrunnelse = model.begrunnelse,
        )
    }

    data class AarsakResponse(
        val type: DeltakerEndring.Aarsak.Type,
        val beskrivelse: String? = null,
    ) {
        constructor(model: DeltakerEndring.Aarsak) : this(
            type = model.type,
            beskrivelse = model.beskrivelse,
        )
    }

    companion object {
        fun fromModel(
            model: DeltakerEndring.Endring,
            oppstartstype: Oppstartstype?,
        ): DeltakerEndringEndringResponse = when (model) {
            is DeltakerEndring.Endring.AvsluttDeltakelse -> AvsluttDeltakelse(model, oppstartstype)
            is DeltakerEndring.Endring.EndreAvslutning -> EndreAvslutning(model)
            is DeltakerEndring.Endring.AvbrytDeltakelse -> AvsluttDeltakelse(model, oppstartstype)
            is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> EndreBakgrunnsinformasjon(model)
            is DeltakerEndring.Endring.EndreDeltakelsesmengde -> EndreDeltakelsesmengde(model)
            is DeltakerEndring.Endring.EndreInnhold -> EndreInnhold(model)
            is DeltakerEndring.Endring.EndreSluttarsak -> EndreSluttarsak(model)
            is DeltakerEndring.Endring.EndreSluttdato -> EndreSluttdato(model)
            is DeltakerEndring.Endring.EndreStartdato -> EndreStartdato(model)
            is DeltakerEndring.Endring.FjernOppstartsdato -> FjernOppstartsdato(model)
            is DeltakerEndring.Endring.ForlengDeltakelse -> ForlengDeltakelse(model)
            is DeltakerEndring.Endring.IkkeAktuell -> IkkeAktuell(model)
            is DeltakerEndring.Endring.ReaktiverDeltakelse -> ReaktiverDeltakelse(model)
        }
    }
}
