package no.nav.tiltaksarrangor.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
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
        val innhold: List<DeltakelsesinnholdResponse.InnholdResponse>,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndreInnhold) : this(
            ledetekst = model.ledetekst,
            innhold = model.innhold.map(DeltakelsesinnholdResponse::InnholdResponse),
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
        val aarsak: DeltakerEndringAarsakResponse,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.IkkeAktuell) : this(
            aarsak = DeltakerEndringAarsakResponse.fromModel(model.aarsak),
            begrunnelse = model.begrunnelse,
        )
    }

    data class AvsluttDeltakelse(
        val aarsak: DeltakerEndringAarsakResponse?,
        val sluttdato: LocalDate,
        val begrunnelse: String?,
        val harFullfort: Boolean,
        val oppstartstype: Oppstartstype,
    ) : DeltakerEndringEndringResponse() {
        constructor(
            model: DeltakerEndring.Endring.AvsluttDeltakelse,
            oppstartstype: Oppstartstype,
        ) : this(
            aarsak = model.aarsak?.let(DeltakerEndringAarsakResponse::fromModel),
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
            harFullfort = true,
            oppstartstype = oppstartstype,
        )

        constructor(
            model: DeltakerEndring.Endring.AvbrytDeltakelse,
            oppstartstype: Oppstartstype,
        ) : this(
            aarsak = DeltakerEndringAarsakResponse.fromModel(model.aarsak),
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
            harFullfort = false,
            oppstartstype = oppstartstype,
        )
    }

    data class EndreAvslutning(
        val aarsak: DeltakerEndringAarsakResponse?,
        val begrunnelse: String?,
        val harFullfort: Boolean?,
        val sluttdato: LocalDate?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndreAvslutning) : this(
            aarsak = model.aarsak?.let(DeltakerEndringAarsakResponse::fromModel),
            begrunnelse = model.begrunnelse,
            harFullfort = model.harFullfort,
            sluttdato = model.sluttdato,
        )
    }

    data class EndreSluttarsak(
        val aarsak: DeltakerEndringAarsakResponse,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndreSluttarsak) : this(
            aarsak = DeltakerEndringAarsakResponse.fromModel(model.aarsak),
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

    data class EndrePrisinfo(
        val prisinfo: Any,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: DeltakerEndring.Endring.EndrePrisinfo) : this(
            prisinfo = model.prisinfo,
        )
    }

    companion object {
        fun fromModel(
            model: DeltakerEndring.Endring,
            oppstartstype: Oppstartstype,
        ): DeltakerEndringEndringResponse = when (model) {
            is DeltakerEndring.Endring.EndrePrisinfo -> EndrePrisinfo(model)
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

    data class DeltakerEndringAarsakResponse(
        val type: Type,
        val beskrivelse: String?,
    ) {
        enum class Type {
            SYK,
            FATT_JOBB,
            TRENGER_ANNEN_STOTTE,
            UTDANNING,
            IKKE_MOTT,
            ANNET,
        }

        companion object {
            fun fromModel(model: DeltakerEndring.Aarsak): DeltakerEndringAarsakResponse = DeltakerEndringAarsakResponse(
                type = Type.valueOf(model.type.name),
                beskrivelse = model.beskrivelse,
            )
        }
    }
}
