package no.nav.amt.lib.models.journalforing.pdf

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import java.time.LocalDate

data class EndringsvedtakPdfDto(
    val deltaker: DeltakerDto,
    val deltakerliste: DeltakerlisteDto,
    val endringer: List<EndringDto>,
    val avsender: AvsenderDto,
    val vedtaksdato: LocalDate,
    val forsteVedtakFattet: LocalDate?,
    val sidetittel: String,
    val ingressnavn: String,
    val opprettetDato: LocalDate,
) {
    data class DeltakerDto(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
        val personident: String,
        val opprettetDato: LocalDate?,
    )

    data class DeltakerlisteDto(
        val navn: String,
        val ledetekst: String,
        val arrangor: ArrangorDto,
        val forskriftskapittel: Int,
        val pameldingstype: GjennomforingPameldingType,
        val harKlagerett: Boolean
    )

    data class ArrangorDto(
        val navn: String,
    )

    data class AvsenderDto(
        val navn: String?,
        val enhet: String,
    )
}

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringDto {
    val tittel: String

    data class EndreDeltakelsesmengde(
        val begrunnelseFraNav: String?,
        val forslagFraArrangor: ForslagDto?,
        val gyldigFra: LocalDate?,
        override val tittel: String,
    ) : EndringDto

    data class EndreStartdato(
        val begrunnelseFraNav: String?,
        val forslagFraArrangor: ForslagDto?,
        override val tittel: String,
    ) : EndringDto

    data class EndreStartdatoOgVarighet(
        val sluttdato: String,
        val begrunnelseFraNav: String?,
        val forslagFraArrangor: ForslagDto?,
        override val tittel: String,
    ) : EndringDto

    data class EndreSluttdato(
        val begrunnelseFraNav: String?,
        val forslagFraArrangor: ForslagDto?,
        override val tittel: String,
    ) : EndringDto

    data class ForlengDeltakelse(
        val begrunnelseFraNav: String?,
        val forslagFraArrangor: ForslagDto?,
        override val tittel: String,
    ) : EndringDto

    data class IkkeAktuell(
        val aarsak: String,
        val begrunnelseFraNav: String?,
        val forslagFraArrangor: ForslagDto?,
        override val tittel: String = "Deltakelsen er ikke aktuell",
    ) : EndringDto

    data class AvsluttDeltakelse(
        val aarsak: String?,
        val begrunnelseFraNav: String?,
        val forslagFraArrangor: ForslagDto?,
        val harDeltatt: String?,
        val harFullfort: String?,
        override val tittel: String,
    ) : EndringDto

    data class EndreAvslutning(
        val aarsak: String?,
        val begrunnelseFraNav: String?,
        val forslagFraArrangor: ForslagDto?,
        val harFullfort: String?,
        val sluttdato: String?,
        override val tittel: String,
    ) : EndringDto

    data class AvbrytDeltakelse(
        val aarsak: String?,
        val begrunnelseFraNav: String?,
        val forslagFraArrangor: ForslagDto?,
        val harDeltatt: String?,
        val harFullfort: String?,
        override val tittel: String,
    ) : EndringDto

    data class EndreBakgrunnsinformasjon(
        val bakgrunnsinformasjon: String?,
        override val tittel: String = "Bakgrunnsinfo er endret",
    ) : EndringDto

    data class EndreInnhold(
        val innhold: List<String>,
        val innholdBeskrivelse: String?,
        override val tittel: String = "Innholdet er endret",
    ) : EndringDto

    data class LeggTilOppstartsdato(
        val sluttdatoFraArrangor: LocalDate?,
        val endringFraArrangor: Boolean = true,
        override val tittel: String,
    ) : EndringDto

    data class FjernOppstartsdato(
        val begrunnelseFraNav: String?,
        val forslagFraArrangor: ForslagDto?,
        override val tittel: String = "Oppstartsdato er fjernet",
    ) : EndringDto

    data class Avslag(
        val aarsak: String,
        val begrunnelseFraNav: String?,
        val vurdering: Vurdering?,
        override val tittel: String = "Søknaden er avslått",
    ) : EndringDto {
        data class Vurdering(
            val type: String,
            val begrunnelse: String?,
        )
    }
}
