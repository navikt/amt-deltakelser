package no.nav.tiltaksarrangor.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Aarsak
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed class DeltakerEndringEndringResponse {
    data class EndreBakgrunnsinformasjon(
        val bakgrunnsinformasjon: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreBakgrunnsinformasjon) : this(
            bakgrunnsinformasjon = model.bakgrunnsinformasjon,
        )
    }

    data class EndreInnhold(
        val ledetekst: String?,
        val innhold: List<Innhold>,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreInnhold) : this(
            ledetekst = model.ledetekst,
            innhold = model.innhold,
        )
    }

    data class EndreDeltakelsesmengde(
        val deltakelsesprosent: Float?,
        val dagerPerUke: Float?,
        val gyldigFra: LocalDate?,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreDeltakelsesmengde) : this(
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
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreStartdato) : this(
            startdato = model.startdato,
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
        )
    }

    data class EndreSluttdato(
        val sluttdato: LocalDate,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreSluttdato) : this(
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
        )
    }

    data class ForlengDeltakelse(
        val sluttdato: LocalDate,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.ForlengDeltakelse) : this(
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
        )
    }

    data class IkkeAktuell(
        val aarsak: Aarsak,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.IkkeAktuell) : this(
            aarsak = model.aarsak,
            begrunnelse = model.begrunnelse,
        )
    }

    data class AvsluttDeltakelse(
        val aarsak: Aarsak?,
        val sluttdato: LocalDate,
        val begrunnelse: String?,
        val harFullfort: Boolean,
        val oppstartstype: Oppstartstype,
    ) : DeltakerEndringEndringResponse() {
        constructor(
            model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.AvsluttDeltakelse,
            oppstartstype: Oppstartstype,
        ) : this(
            aarsak = model.aarsak,
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
            harFullfort = true,
            oppstartstype = oppstartstype,
        )

        constructor(
            model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.AvbrytDeltakelse,
            oppstartstype: Oppstartstype,
        ) : this(
            aarsak = model.aarsak,
            sluttdato = model.sluttdato,
            begrunnelse = model.begrunnelse,
            harFullfort = false,
            oppstartstype = oppstartstype,
        )
    }

    data class EndreAvslutning(
        val aarsak: Aarsak?,
        val begrunnelse: String?,
        val harFullfort: Boolean?,
        val sluttdato: LocalDate?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreAvslutning) : this(
            aarsak = model.aarsak,
            begrunnelse = model.begrunnelse,
            harFullfort = model.harFullfort,
            sluttdato = model.sluttdato,
        )
    }

    data class EndreSluttarsak(
        val aarsak: Aarsak,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreSluttarsak) : this(
            aarsak = model.aarsak,
            begrunnelse = model.begrunnelse,
        )
    }

    data class ReaktiverDeltakelse(
        val reaktivertDato: LocalDate,
        val begrunnelse: String,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.ReaktiverDeltakelse) : this(
            reaktivertDato = model.reaktivertDato,
            begrunnelse = model.begrunnelse,
        )
    }

    data class FjernOppstartsdato(
        val begrunnelse: String?,
    ) : DeltakerEndringEndringResponse() {
        constructor(model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.FjernOppstartsdato) : this(
            begrunnelse = model.begrunnelse,
        )
    }

    companion object {
        fun fromModel(
            model: no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring,
            oppstartstype: Oppstartstype,
        ): DeltakerEndringEndringResponse = when (model) {
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.AvsluttDeltakelse -> AvsluttDeltakelse(model, oppstartstype)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreAvslutning -> EndreAvslutning(model)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.AvbrytDeltakelse -> AvsluttDeltakelse(model, oppstartstype)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> EndreBakgrunnsinformasjon(model)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreDeltakelsesmengde -> EndreDeltakelsesmengde(model)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreInnhold -> EndreInnhold(model)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreSluttarsak -> EndreSluttarsak(model)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreSluttdato -> EndreSluttdato(model)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndreStartdato -> EndreStartdato(model)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.FjernOppstartsdato -> FjernOppstartsdato(model)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.ForlengDeltakelse -> ForlengDeltakelse(model)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.IkkeAktuell -> IkkeAktuell(model)
            is no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.ReaktiverDeltakelse -> ReaktiverDeltakelse(model)
        }
    }
}
