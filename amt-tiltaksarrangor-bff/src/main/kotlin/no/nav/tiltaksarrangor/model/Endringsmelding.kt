package no.nav.tiltaksarrangor.model

import java.time.LocalDate
import java.util.UUID

data class Endringsmelding(
	val id: UUID,
	val innhold: Innhold?,
	val type: Type,
	val status: Status,
	val sendt: LocalDate,
) {
	enum class Type {
		LEGG_TIL_OPPSTARTSDATO,
		ENDRE_OPPSTARTSDATO,
		FORLENG_DELTAKELSE,
		AVSLUTT_DELTAKELSE,
		DELTAKER_IKKE_AKTUELL,
		ENDRE_DELTAKELSE_PROSENT,
		ENDRE_SLUTTDATO,
		ENDRE_SLUTTAARSAK,
	}

	enum class Status {
		AKTIV,
		TILBAKEKALT,
		UTDATERT,
		UTFORT,
	}

	sealed class Innhold {
		data class LeggTilOppstartsdatoInnhold(
			val oppstartsdato: LocalDate,
		) : Innhold()

		data class EndreOppstartsdatoInnhold(
			val oppstartsdato: LocalDate?,
		) : Innhold()

		data class EndreDeltakelseProsentInnhold(
			val deltakelseProsent: Int,
			val dagerPerUke: Int?,
			val gyldigFraDato: LocalDate?,
		) : Innhold()

		data class ForlengDeltakelseInnhold(
			val sluttdato: LocalDate,
		) : Innhold()

		data class AvsluttDeltakelseInnhold(
			val sluttdato: LocalDate,
			val aarsak: DeltakerStatusAarsakJsonDboDto,
		) : Innhold()

		data class DeltakerIkkeAktuellInnhold(
			val aarsak: DeltakerStatusAarsakJsonDboDto,
		) : Innhold()

		data class EndreSluttdatoInnhold(
			val sluttdato: LocalDate,
		) : Innhold()

		data class EndreSluttaarsakInnhold(
			val aarsak: DeltakerStatusAarsakJsonDboDto,
		) : Innhold()
	}
}
