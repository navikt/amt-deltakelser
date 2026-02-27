package no.nav.amt.lib.models.journalforing.pdf

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ForslagDto.EndreDeltakelsesmengde::class, name = "Endre deltakelsesmengde"),
    JsonSubTypes.Type(value = ForslagDto.EndreStartdato::class, name = "Endre oppstartsdato"),
    JsonSubTypes.Type(value = ForslagDto.EndreStartdatoOgVarighet::class, name = "Endre oppstartsdato og varighet"),
    JsonSubTypes.Type(value = ForslagDto.EndreSluttdato::class, name = "Endre sluttdato"),
    JsonSubTypes.Type(value = ForslagDto.ForlengDeltakelse::class, name = "Forleng deltakelse"),
    JsonSubTypes.Type(value = ForslagDto.IkkeAktuell::class, name = "Er ikke aktuell"),
    JsonSubTypes.Type(value = ForslagDto.AvsluttDeltakelse::class, name = "Avslutt deltakelse"),
    JsonSubTypes.Type(value = ForslagDto.FjernOppstartsdato::class, name = "Fjern oppstartsdato"),
    JsonSubTypes.Type(value = ForslagDto.EndreAvslutning::class, name = "Endre avslutning"),
)
sealed interface ForslagDto {
    data class EndreDeltakelsesmengde(
        val deltakelsesmengdeTekst: String,
        val begrunnelseFraArrangor: String?,
    ) : ForslagDto

    data class EndreStartdato(
        val startdato: LocalDate?,
        val begrunnelseFraArrangor: String?,
    ) : ForslagDto

    data class EndreStartdatoOgVarighet(
        val startdato: LocalDate?,
        val sluttdato: LocalDate,
        val begrunnelseFraArrangor: String?,
    ) : ForslagDto

    data class EndreSluttdato(
        val sluttdato: LocalDate,
        val begrunnelseFraArrangor: String?,
    ) : ForslagDto

    data class ForlengDeltakelse(
        val sluttdato: LocalDate,
        val begrunnelseFraArrangor: String?,
    ) : ForslagDto

    data class IkkeAktuell(
        val aarsak: String,
        val begrunnelseFraArrangor: String?,
    ) : ForslagDto

    data class AvsluttDeltakelse(
        val aarsak: String?,
        val sluttdato: LocalDate?,
        val harDeltatt: String?,
        val harFullfort: String?,
        val begrunnelseFraArrangor: String?,
    ) : ForslagDto

    data class EndreAvslutning(
        val aarsak: String?,
        val harDeltatt: String?,
        val harFullfort: String?,
        val begrunnelseFraArrangor: String?,
    ) : ForslagDto

    data class FjernOppstartsdato(
        val begrunnelseFraArrangor: String?,
    ) : ForslagDto
}
