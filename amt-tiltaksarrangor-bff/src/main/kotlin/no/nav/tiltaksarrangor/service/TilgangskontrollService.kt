package no.nav.tiltaksarrangor.service

import no.nav.tiltaksarrangor.model.exceptions.SkjultDeltakerException
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerMedDeltakerlisteDbo
import org.springframework.stereotype.Service

@Service
class TilgangskontrollService(
	private val ansattService: AnsattService,
) {
	fun verifiserTilgangTilDeltaker(ansatt: AnsattDbo, deltakerMedDeltakerliste: DeltakerMedDeltakerlisteDbo) {
		val deltaker = deltakerMedDeltakerliste.deltaker
		val deltakerliste = deltakerMedDeltakerliste.deltakerliste

		val harTilgangTilDeltaker = ansattService.harTilgangTilDeltaker(
			deltakerId = deltaker.id,
			deltakerlisteId = deltakerliste.id,
			deltakerlisteArrangorId = deltakerliste.arrangorId,
			ansattDbo = ansatt,
		)

		if (!harTilgangTilDeltaker) {
			throw UnauthorizedException("Ansatt ${ansatt.id} har ikke tilgang til deltaker med id ${deltaker.id}")
		}

		verifiserDeltakerIkkeErSkjult(deltaker)
	}

	fun verifiserDeltakerIkkeErSkjult(deltaker: DeltakerDbo) {
		if (deltaker.erSkjult()) {
			throw SkjultDeltakerException("Deltaker med id ${deltaker.id} er skjult for tiltaksarrangør")
		}
	}

	fun verifiserTilgangTilDeltakerOgMeldinger(ansatt: AnsattDbo, deltakerMedDeltakerliste: DeltakerMedDeltakerlisteDbo) {
		val deltaker = deltakerMedDeltakerliste.deltaker
		verifiserTilgangTilDeltaker(ansatt, deltakerMedDeltakerliste)
		verifiserDeltakerIkkeErSkjult(deltaker)

		if (!ansattService.harTilgangTilEndringsmeldingerOgVurderingForDeltaker(deltakerMedDeltakerliste, ansatt)) {
			throw UnauthorizedException("Ansatt ${ansatt.id} har ikke tilgang til deltaker med id ${deltaker.id}")
		}
	}
}
