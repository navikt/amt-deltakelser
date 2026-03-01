package no.nav.amt.deltaker.bff.deltaker.api.model

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Aarsak
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed class DeltakerEndringEndringDto {
    data class EndreBakgrunnsinformasjon(
        val bakgrunnsinformasjon: String?,
    ) : DeltakerEndringEndringDto()

    data class EndreInnhold(
        val ledetekst: String?,
        val innhold: List<Innhold>,
    ) : DeltakerEndringEndringDto()

    data class EndreDeltakelsesmengde(
        val deltakelsesprosent: Float?,
        val dagerPerUke: Float?,
        val gyldigFra: LocalDate?,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringDto()

    data class EndreStartdato(
        val startdato: LocalDate?,
        val sluttdato: LocalDate?,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringDto()

    data class EndreSluttdato(
        val sluttdato: LocalDate,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringDto()

    data class ForlengDeltakelse(
        val sluttdato: LocalDate,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringDto()

    data class IkkeAktuell(
        val aarsak: Aarsak,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringDto()

    data class AvsluttDeltakelse(
        val aarsak: Aarsak?,
        val sluttdato: LocalDate,
        val begrunnelse: String?,
        val harFullfort: Boolean,
        val oppstartstype: Oppstartstype?,
    ) : DeltakerEndringEndringDto()

    data class EndreAvslutning(
        val aarsak: Aarsak?,
        val sluttdato: LocalDate?,
        val begrunnelse: String?,
        val harFullfort: Boolean?,
    ) : DeltakerEndringEndringDto()

    data class EndreSluttarsak(
        val aarsak: Aarsak,
        val begrunnelse: String?,
    ) : DeltakerEndringEndringDto()

    data class ReaktiverDeltakelse(
        val reaktivertDato: LocalDate,
        val begrunnelse: String,
    ) : DeltakerEndringEndringDto()

    data class FjernOppstartsdato(
        val begrunnelse: String?,
    ) : DeltakerEndringEndringDto()

    companion object {
        fun fromEndring(endring: DeltakerEndring.Endring, oppstartstype: Oppstartstype?): DeltakerEndringEndringDto = with(endring) {
            when (this) {
                is DeltakerEndring.Endring.AvsluttDeltakelse -> AvsluttDeltakelse(
                    aarsak = aarsak,
                    sluttdato = sluttdato,
                    begrunnelse = begrunnelse,
                    harFullfort = true,
                    oppstartstype = oppstartstype,
                )

                is DeltakerEndring.Endring.EndreAvslutning -> EndreAvslutning(
                    aarsak = aarsak,
                    begrunnelse = begrunnelse,
                    harFullfort = harFullfort,
                    sluttdato = sluttdato,
                )

                is DeltakerEndring.Endring.AvbrytDeltakelse -> AvsluttDeltakelse(
                    aarsak = aarsak,
                    sluttdato = sluttdato,
                    begrunnelse = begrunnelse,
                    harFullfort = false,
                    oppstartstype = oppstartstype,
                )

                is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> EndreBakgrunnsinformasjon(
                    bakgrunnsinformasjon,
                )

                is DeltakerEndring.Endring.EndreDeltakelsesmengde -> EndreDeltakelsesmengde(
                    deltakelsesprosent,
                    dagerPerUke,
                    gyldigFra,
                    begrunnelse,
                )

                is DeltakerEndring.Endring.EndreInnhold -> EndreInnhold(ledetekst, innhold)
                is DeltakerEndring.Endring.EndreSluttarsak -> EndreSluttarsak(aarsak, begrunnelse)
                is DeltakerEndring.Endring.EndreSluttdato -> EndreSluttdato(sluttdato, begrunnelse)
                is DeltakerEndring.Endring.EndreStartdato -> EndreStartdato(startdato, sluttdato, begrunnelse)
                is DeltakerEndring.Endring.FjernOppstartsdato -> FjernOppstartsdato(begrunnelse)
                is DeltakerEndring.Endring.ForlengDeltakelse -> ForlengDeltakelse(sluttdato, begrunnelse)
                is DeltakerEndring.Endring.IkkeAktuell -> IkkeAktuell(aarsak, begrunnelse)
                is DeltakerEndring.Endring.ReaktiverDeltakelse -> ReaktiverDeltakelse(
                    reaktivertDato,
                    begrunnelse,
                )
            }
        }
    }
}
