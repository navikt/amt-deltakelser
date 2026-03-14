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
	) : DeltakerEndringEndringResponse()

	data class EndreInnhold(
		val ledetekst: String?,
		val innhold: List<Innhold>,
	) : DeltakerEndringEndringResponse()

	data class EndreDeltakelsesmengde(
		val deltakelsesprosent: Float?,
		val dagerPerUke: Float?,
		val gyldigFra: LocalDate?,
		val begrunnelse: String?,
	) : DeltakerEndringEndringResponse()

	data class EndreStartdato(
		val startdato: LocalDate?,
		val sluttdato: LocalDate?,
		val begrunnelse: String?,
	) : DeltakerEndringEndringResponse()

	data class EndreSluttdato(
		val sluttdato: LocalDate,
		val begrunnelse: String?,
	) : DeltakerEndringEndringResponse()

	data class ForlengDeltakelse(
		val sluttdato: LocalDate,
		val begrunnelse: String?,
	) : DeltakerEndringEndringResponse()

	data class IkkeAktuell(
		val aarsak: Aarsak,
		val begrunnelse: String?,
	) : DeltakerEndringEndringResponse()

	data class AvsluttDeltakelse(
		val aarsak: Aarsak?,
		val sluttdato: LocalDate,
		val begrunnelse: String?,
		val harFullfort: Boolean,
		val oppstartstype: Oppstartstype,
	) : DeltakerEndringEndringResponse()

	data class EndreAvslutning(
		val aarsak: Aarsak?,
		val begrunnelse: String?,
		val harFullfort: Boolean?,
		val sluttdato: LocalDate?,
	) : DeltakerEndringEndringResponse()

	data class EndreSluttarsak(
		val aarsak: Aarsak,
		val begrunnelse: String?,
	) : DeltakerEndringEndringResponse()

	data class ReaktiverDeltakelse(
		val reaktivertDato: LocalDate,
		val begrunnelse: String,
	) : DeltakerEndringEndringResponse()

	data class FjernOppstartsdato(
		val begrunnelse: String?,
	) : DeltakerEndringEndringResponse()
}
