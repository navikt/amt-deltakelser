package no.nav.tiltaksarrangor.melding.endring

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun validerLeggTilOppstartsdato(
	startdato: LocalDate,
	sluttdato: LocalDate?,
	deltaker: DeltakerDbo,
	deltakerliste: DeltakerlisteDbo,
) {
	require(deltaker.startdato == null) {
		"Kan ikke legge til ny oppstartsdato når startdato finnes fra før"
	}

	validerOppstartsdato(startdato, sluttdato, deltakerliste)
}

private fun validerOppstartsdato(
	startdato: LocalDate,
	sluttdato: LocalDate?,
	deltakerliste: DeltakerlisteDbo,
) {
	require(!startdato.isBefore(deltakerliste.startDato)) {
		"Startdato kan ikke være tidligere enn deltakerlistens startdato"
	}
	sluttdato?.let { validerSluttdato(it, startdato, deltakerliste) }
}

private fun validerSluttdato(
	sluttdato: LocalDate,
	startdato: LocalDate?,
	deltakerliste: DeltakerlisteDbo,
) {
	require(deltakerliste.sluttDato == null || !sluttdato.isAfter(deltakerliste.sluttDato)) {
		"Sluttdato kan ikke være senere enn deltakerlistens sluttdato"
	}
	require(startdato == null || !sluttdato.isBefore(startdato)) {
		"Sluttdato må være etter startdato"
	}

	startdato?.let { validerVarighet(it, sluttdato, maxVarighet(deltakerliste)) }
}

private fun validerVarighet(
	startdato: LocalDate,
	sluttdato: LocalDate,
	maxVarighet: Duration?,
) {
	if (maxVarighet == null) return

	val senesteSluttdato = startdato.plusDays(maxVarighet.toDays())

	require(!sluttdato.isAfter(senesteSluttdato)) {
		"Sluttdato $sluttdato er etter seneste mulige sluttdato $senesteSluttdato"
	}
}

val HELLIGDAGER: Duration = weeks(1)

private fun maxVarighet(deltakerliste: DeltakerlisteDbo): Duration? = when (deltakerliste.tiltakskode) {
	Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
	Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
	Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
	-> years(3)

	Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK -> weeks(8) + HELLIGDAGER
	Tiltakskode.ARBEIDSFORBEREDENDE_TRENING -> years(2)

	Tiltakskode.AVKLARING,
	Tiltakskode.ARBEIDSRETTET_REHABILITERING,
	-> weeks(12)

	Tiltakskode.OPPFOLGING -> years(1)

	Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
	Tiltakskode.STUDIESPESIALISERING,
	Tiltakskode.HOYERE_UTDANNING,
	Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
	-> years(4)
	Tiltakskode.FAG_OG_YRKESOPPLAERING,
	-> years(5)

	Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
	Tiltakskode.JOBBKLUBB,
	-> null

	else -> throw NotImplementedError("maxVarighet for tiltakstype: ${deltakerliste.tiltakskode} er ikke implementert")
}

private fun years(n: Long) = Duration.of(n * 365, ChronoUnit.DAYS)

private fun weeks(n: Long) = Duration.of(n * 7, ChronoUnit.DAYS)
