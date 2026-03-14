package no.nav.tiltaksarrangor.consumer.model

import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.tiltaksarrangor.model.DeltakerStatusAarsakJsonDboDto
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.STATUSER_SOM_KAN_SKJULES
import java.time.LocalDateTime

fun DeltakerKafkaPayload.toDeltakerDbo(lagretDeltaker: DeltakerDbo? = null): DeltakerDbo {
	val oppdatertStatus = status.type

	val skalFortsattSkjules = lagretDeltaker?.erSkjult() == true && oppdatertStatus in STATUSER_SOM_KAN_SKJULES

	return DeltakerDbo(
		id = id,
		deltakerlisteId = deltakerliste.id,
		personident = personalia.personident,
		fornavn = personalia.navn.fornavn,
		mellomnavn = personalia.navn.mellomnavn,
		etternavn = personalia.navn.etternavn,
		telefonnummer = personalia.kontaktinformasjon.telefonnummer,
		epost = personalia.kontaktinformasjon.epost,
		erSkjermet = personalia.skjermet,
		adresse = personalia.adresse?.let { AdresseJsonDbo.fromModel(it) },
		status = oppdatertStatus,
		statusOpprettetDato = status.opprettetDato,
		statusGyldigFraDato = status.gyldigFra,
		statusAarsak = status.aarsak?.let { DeltakerStatusAarsakJsonDboDto(it, status.aarsaksbeskrivelse) },
		dagerPerUke = dagerPerUke,
		prosentStilling = prosentStilling,
		startdato = oppstartsdato,
		sluttdato = sluttdato,
		innsoktDato = innsoktDato,
		// I en overgangsfase ønsker vi å beholde bestillingstekster fra arena som er lagret i bffen inntil nav-veileder endrer bakgrunnsinfo i ny løsning.
		// Hvis man altid lagrer bestillingTekst fra dto, vil den være null for alle deltakere vi har lest inn i fra Arena.
		bestillingstekst = bestillingTekst ?: lagretDeltaker?.bestillingstekst,
		navKontor = navKontor,
		navVeilederId = navVeileder?.id,
		navVeilederNavn = navVeileder?.navn,
		navVeilederEpost = navVeileder?.epost,
		navVeilederTelefon = navVeileder?.telefon,
		skjultAvAnsattId =
			if (skalFortsattSkjules) {
				lagretDeltaker.skjultAvAnsattId
			} else {
				null
			},
		skjultDato =
			if (skalFortsattSkjules) {
				lagretDeltaker.skjultDato
			} else {
				null
			},
		vurderingerFraArrangor = vurderingerFraArrangor,
		adressebeskyttet = personalia.adressebeskyttelse != null,
		innhold = innhold,
		kilde = kilde,
		historikk = historikk ?: emptyList(),
		sistEndret = sistEndret ?: LocalDateTime.now(),
		forsteVedtakFattet = forsteVedtakFattet,
		erManueltDeltMedArrangor = erManueltDeltMedArrangor,
		oppfolgingsperioder = oppfolgingsperioder,
	)
}

val SKJULES_ALLTID_STATUSER =
	listOf(
		DeltakerStatus.Type.VENTELISTE,
		DeltakerStatus.Type.PABEGYNT_REGISTRERING,
		DeltakerStatus.Type.FEILREGISTRERT,
		DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
		DeltakerStatus.Type.AVBRUTT_UTKAST,
	)

val AVSLUTTENDE_STATUSER =
	listOf(
		DeltakerStatus.Type.HAR_SLUTTET,
		DeltakerStatus.Type.IKKE_AKTUELL,
		DeltakerStatus.Type.AVBRUTT,
		DeltakerStatus.Type.FULLFORT,
	)
