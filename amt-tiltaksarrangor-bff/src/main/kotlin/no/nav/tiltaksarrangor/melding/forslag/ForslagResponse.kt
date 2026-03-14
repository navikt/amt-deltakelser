package no.nav.tiltaksarrangor.melding.forslag

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.Forslag
import java.time.LocalDateTime
import java.util.UUID

data class ForslagResponse(
	val id: UUID,
	val opprettetAvArrangor: String,
	val opprettet: LocalDateTime,
	val begrunnelse: String,
	val endring: Forslag.Endring,
	val status: Status,
) {
	@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
	sealed interface Status {
		data class Godkjent(
			val godkjentAv: NavAnsatt,
			val godkjent: LocalDateTime,
		) : Status

		data class Avvist(
			val avvistAv: NavAnsatt,
			val avvist: LocalDateTime,
			val begrunnelseFraNav: String,
		) : Status

		data class Tilbakekalt(
			val tilbakekaltAvArrangor: String,
			val tilbakekalt: LocalDateTime,
		) : Status

		data class Erstattet(
			val erstattet: LocalDateTime,
		)

		data object VenterPaSvar : Status
	}

	data class NavAnsatt(
		val navn: String,
		val enhet: String,
	)
}

data class AktivtForslagResponse(
	val id: UUID,
	val opprettet: LocalDateTime,
	val begrunnelse: String?,
	val endring: Forslag.Endring,
	val status: ForslagResponse.Status.VenterPaSvar = ForslagResponse.Status.VenterPaSvar,
)

fun Forslag.tilAktivtForslagResponse(): AktivtForslagResponse {
	require(this.status is Forslag.Status.VenterPaSvar) {
		"Forslag ${this.id} kan ikke mappes til AktivtForslagResponse pga feil status"
	}
	return AktivtForslagResponse(
		this.id,
		this.opprettet,
		this.begrunnelse,
		this.endring,
	)
}
