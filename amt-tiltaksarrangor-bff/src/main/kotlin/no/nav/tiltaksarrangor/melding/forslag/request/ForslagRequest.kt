package no.nav.tiltaksarrangor.melding.forslag.request

import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import java.time.LocalDate

sealed interface ForslagRequest {
	val begrunnelse: String?
}

data class ForlengDeltakelseRequest(
	val sluttdato: LocalDate,
	override val begrunnelse: String,
) : ForslagRequest

data class AvsluttDeltakelseRequest(
	val sluttdato: LocalDate?,
	val aarsak: EndringAarsak?,
	override val begrunnelse: String?,
	val harFullfort: Boolean?,
	val harDeltatt: Boolean?,
) : ForslagRequest

data class EndreAvslutningRequest(
	val aarsak: EndringAarsak?,
	override val begrunnelse: String?,
	val harFullfort: Boolean?,
	val harDeltatt: Boolean?,
	val sluttdato: LocalDate?,
) : ForslagRequest

data class IkkeAktuellRequest(
	val aarsak: EndringAarsak,
	override val begrunnelse: String?,
) : ForslagRequest

data class DeltakelsesmengdeRequest(
	val deltakelsesprosent: Int,
	val dagerPerUke: Int?,
	val gyldigFra: LocalDate?,
	override val begrunnelse: String,
) : ForslagRequest

data class SluttdatoRequest(
	val sluttdato: LocalDate,
	override val begrunnelse: String,
) : ForslagRequest

data class StartdatoRequest(
	val startdato: LocalDate,
	val sluttdato: LocalDate?,
	override val begrunnelse: String,
) : ForslagRequest

data class SluttarsakRequest(
	val aarsak: EndringAarsak,
	override val begrunnelse: String?,
) : ForslagRequest

data class FjernOppstartsdatoRequest(
	override val begrunnelse: String,
) : ForslagRequest
