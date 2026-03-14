package no.nav.tiltaksarrangor.model

import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.tiltaksarrangor.repositories.model.EndringsmeldingDbo
import java.time.LocalDate
import java.util.UUID

data class AktivEndring(
	val deltakerId: UUID,
	val endingsType: EndringsType,
	val type: Type,
	val sendt: LocalDate,
) {
	enum class Type {
		Forslag,
		Endringsmelding,
	}

	enum class EndringsType {
		LeggTilOppstartsDato,
		ForlengDeltakelse,
		IkkeAktuell,
		AvsluttDeltakelse,
		Deltakelsesmengde,
		Sluttdato,
		Startdato,
		Sluttarsak,
		FjernOppstartsdato,
		EndreAvslutning,
	}
}

fun getTypeFromEndringsmelding(endringsmeldingtype: Endringsmelding.Type): AktivEndring.EndringsType = when (endringsmeldingtype) {
	Endringsmelding.Type.ENDRE_OPPSTARTSDATO -> AktivEndring.EndringsType.Startdato
	Endringsmelding.Type.LEGG_TIL_OPPSTARTSDATO -> AktivEndring.EndringsType.LeggTilOppstartsDato
	Endringsmelding.Type.FORLENG_DELTAKELSE -> AktivEndring.EndringsType.ForlengDeltakelse
	Endringsmelding.Type.DELTAKER_IKKE_AKTUELL -> AktivEndring.EndringsType.IkkeAktuell
	Endringsmelding.Type.ENDRE_SLUTTAARSAK -> AktivEndring.EndringsType.Sluttarsak
	Endringsmelding.Type.AVSLUTT_DELTAKELSE -> AktivEndring.EndringsType.AvsluttDeltakelse
	Endringsmelding.Type.ENDRE_DELTAKELSE_PROSENT -> AktivEndring.EndringsType.Deltakelsesmengde
	Endringsmelding.Type.ENDRE_SLUTTDATO -> AktivEndring.EndringsType.Sluttdato
}

fun getTypeFromForslag(endring: Forslag.Endring): AktivEndring.EndringsType = when (endring) {
	is Forslag.ForlengDeltakelse -> AktivEndring.EndringsType.ForlengDeltakelse
	is Forslag.IkkeAktuell -> AktivEndring.EndringsType.IkkeAktuell
	is Forslag.Sluttarsak -> AktivEndring.EndringsType.Sluttarsak
	is Forslag.AvsluttDeltakelse -> AktivEndring.EndringsType.AvsluttDeltakelse
	is Forslag.Deltakelsesmengde -> AktivEndring.EndringsType.Deltakelsesmengde
	is Forslag.Sluttdato -> AktivEndring.EndringsType.Sluttdato
	is Forslag.Startdato -> AktivEndring.EndringsType.Startdato
	is Forslag.FjernOppstartsdato -> AktivEndring.EndringsType.FjernOppstartsdato
	is Forslag.EndreAvslutning -> AktivEndring.EndringsType.EndreAvslutning
}

fun getAktivEndring(
	deltakerId: UUID,
	endringsmeldinger: List<EndringsmeldingDbo>,
	aktiveForslag: List<Forslag>,
	erKometMasterForTiltakstype: Boolean,
): AktivEndring? {
	val aktiveForslagForDeltaker = getAktiveForslag(deltakerId, aktiveForslag)
	if (aktiveForslagForDeltaker.isNotEmpty()) {
		return aktiveForslagForDeltaker
			.map {
				AktivEndring(
					deltakerId,
					endingsType = getTypeFromForslag(it.endring),
					type = AktivEndring.Type.Forslag,
					sendt = it.opprettet.toLocalDate(),
				)
			}.maxByOrNull { it.sendt }
	}
	if (!erKometMasterForTiltakstype) {
		val endringsmeldingerForDeltaker = getEndringsmeldinger(deltakerId, endringsmeldinger)
		if (endringsmeldingerForDeltaker.isNotEmpty()) {
			return endringsmeldingerForDeltaker
				.map {
					AktivEndring(
						deltakerId,
						endingsType = getTypeFromEndringsmelding(it.type),
						type = AktivEndring.Type.Endringsmelding,
						sendt = it.sendt,
					)
				}.maxBy { it.sendt }
		}
	}
	return null
}

private fun getEndringsmeldinger(deltakerId: UUID, endringsmeldinger: List<EndringsmeldingDbo>): List<Endringsmelding> {
	val endringsmeldingerForDeltaker = endringsmeldinger.filter { it.deltakerId == deltakerId }
	return endringsmeldingerForDeltaker.map { it.toEndringsmelding() }
}

private fun getAktiveForslag(deltakerId: UUID, aktiveForslag: List<Forslag>): List<Forslag> = aktiveForslag.filter {
	it.deltakerId == deltakerId
}
