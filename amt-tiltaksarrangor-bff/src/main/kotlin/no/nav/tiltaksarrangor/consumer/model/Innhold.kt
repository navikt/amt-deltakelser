package no.nav.tiltaksarrangor.consumer.model

import no.nav.tiltaksarrangor.model.DeltakerStatusAarsakJsonDboDto
import no.nav.tiltaksarrangor.model.Endringsmelding
import java.time.LocalDate

sealed class Innhold {
	data class LeggTilOppstartsdatoInnhold(
		val oppstartsdato: LocalDate,
	) : Innhold()

	data class EndreOppstartsdatoInnhold(
		val oppstartsdato: LocalDate?,
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

	data class EndreDeltakelseProsentInnhold(
		val nyDeltakelseProsent: Int,
		val dagerPerUke: Int?,
		val gyldigFraDato: LocalDate?,
	) : Innhold()

	data class EndreSluttdatoInnhold(
		val sluttdato: LocalDate,
	) : Innhold()

	data class EndreSluttaarsakInnhold(
		val aarsak: DeltakerStatusAarsakJsonDboDto,
	) : Innhold()
}

fun Innhold.toEndringsmeldingInnhold(): Endringsmelding.Innhold = when (this) {
	is Innhold.LeggTilOppstartsdatoInnhold -> Endringsmelding.Innhold.LeggTilOppstartsdatoInnhold(this.oppstartsdato)
	is Innhold.EndreOppstartsdatoInnhold -> Endringsmelding.Innhold.EndreOppstartsdatoInnhold(this.oppstartsdato)
	is Innhold.ForlengDeltakelseInnhold -> Endringsmelding.Innhold.ForlengDeltakelseInnhold(this.sluttdato)
	is Innhold.EndreDeltakelseProsentInnhold ->
		Endringsmelding.Innhold.EndreDeltakelseProsentInnhold(
			this.nyDeltakelseProsent,
			this.dagerPerUke,
			this.gyldigFraDato,
		)
	is Innhold.AvsluttDeltakelseInnhold -> Endringsmelding.Innhold.AvsluttDeltakelseInnhold(this.sluttdato, this.aarsak)
	is Innhold.DeltakerIkkeAktuellInnhold -> Endringsmelding.Innhold.DeltakerIkkeAktuellInnhold(this.aarsak)
	is Innhold.EndreSluttdatoInnhold -> Endringsmelding.Innhold.EndreSluttdatoInnhold(this.sluttdato)
	is Innhold.EndreSluttaarsakInnhold -> Endringsmelding.Innhold.EndreSluttaarsakInnhold(this.aarsak)
}
