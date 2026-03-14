package no.nav.tiltaksarrangor.veileder.service

import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.melding.forslag.ForslagRepository
import no.nav.tiltaksarrangor.model.DeltakerStatusInternalModel
import no.nav.tiltaksarrangor.model.Endringsmelding
import no.nav.tiltaksarrangor.model.UlestEndring
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.model.getAktivEndring
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.UlestEndringRepository
import no.nav.tiltaksarrangor.repositories.model.DeltakerMedDeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.EndringsmeldingDbo
import no.nav.tiltaksarrangor.repositories.model.VeilederDeltakerDbo
import no.nav.tiltaksarrangor.service.AnsattService
import no.nav.tiltaksarrangor.veileder.model.Deltaker
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class VeilederService(
	private val ansattService: AnsattService,
	private val deltakerRepository: DeltakerRepository,
	private val forslagRepository: ForslagRepository,
	private val endringsmeldingRepository: EndringsmeldingRepository,
	private val ulestEndringRepository: UlestEndringRepository,
	private val unleashToggle: CommonUnleashToggle,
) {
	fun getMineDeltakere(personIdent: String): List<Deltaker> {
		val ansatt = ansattService.getAnsatt(personIdent) ?: throw UnauthorizedException("Ansatt finnes ikke")
		if (!ansattService.erVeileder(ansatt.roller)) {
			throw UnauthorizedException("Ansatt ${ansatt.id} er ikke veileder hos noen arrangører")
		}
		if (ansatt.veilederDeltakere.isEmpty()) {
			return emptyList()
		}

		val deltakere =
			deltakerRepository
				.getDeltakereMedDeltakerliste(ansatt.veilederDeltakere.map { it.deltakerId })
				.filter { ansattService.harRolleHosArrangor(it.deltakerliste.arrangorId, AnsattRolle.VEILEDER, ansatt.roller) }
				.filter { !it.deltaker.erSkjult() }
				.filter { it.deltaker.skalVises() }
				.filter { it.deltakerliste.erTilgjengeligForArrangor() }

		if (deltakere.isEmpty()) {
			return emptyList()
		}
		val endringsmeldinger = endringsmeldingRepository.getEndringsmeldingerForDeltakere(deltakere.map { it.deltaker.id })
		val aktiveForslag = forslagRepository.getAktiveForslagForDeltakere(deltakere.map { it.deltaker.id })
		val ulesteEndringer = ulestEndringRepository.getUlesteForslagForDeltakere(deltakere.map { it.deltaker.id })

		return tilVeiledersDeltakere(deltakere, ansatt.veilederDeltakere, endringsmeldinger, aktiveForslag, ulesteEndringer)
	}

	private fun tilVeiledersDeltakere(
		deltakere: List<DeltakerMedDeltakerlisteDbo>,
		ansattsVeilederDeltakere: List<VeilederDeltakerDbo>,
		endringsmeldinger: List<EndringsmeldingDbo>,
		aktiveForslag: List<Forslag>,
		ulesteEndringer: List<UlestEndring>,
	): List<Deltaker> = deltakere.map {
		val erKometMasterForTiltakstype = unleashToggle.erKometMasterForTiltakstype(it.deltakerliste.tiltakskode)
		val adressebeskyttet = it.deltaker.adressebeskyttet

		Deltaker(
			id = it.deltaker.id,
			deltakerliste =
				Deltaker.Deltakerliste(
					id = it.deltakerliste.id,
					type = it.deltakerliste.tiltaksnavn,
					navn = it.deltakerliste.navn,
				),
			fornavn = if (adressebeskyttet) "" else it.deltaker.fornavn,
			mellomnavn = if (adressebeskyttet) null else it.deltaker.mellomnavn,
			etternavn = if (adressebeskyttet) "" else it.deltaker.etternavn,
			fodselsnummer = if (adressebeskyttet) "" else it.deltaker.personident,
			status =
				DeltakerStatusInternalModel(
					type = it.deltaker.status,
					endretDato = it.deltaker.statusGyldigFraDato,
					aarsak = it.deltaker.statusAarsak,
				),
			startDato = it.deltaker.startdato,
			sluttDato = it.deltaker.sluttdato,
			veiledertype = getVeiledertype(it.deltaker.id, ansattsVeilederDeltakere),
			aktiveEndringsmeldinger = if (erKometMasterForTiltakstype) {
				emptyList()
			} else {
				getEndringsmeldinger(it.deltaker.id, endringsmeldinger)
			},
			aktivEndring = getAktivEndring(it.deltaker.id, endringsmeldinger, aktiveForslag, erKometMasterForTiltakstype),
			sistEndret = it.deltaker.sistEndret,
			adressebeskyttet = adressebeskyttet,
			svarFraNav = ulesteEndringer.any { ulestEndring -> ulestEndring.deltakerId == it.deltaker.id && ulestEndring.erSvarFraNav() },
			oppdateringFraNav = ulesteEndringer.any { ulestEndring ->
				ulestEndring.deltakerId == it.deltaker.id && ulestEndring.erOppdateringFraNav()
			},
			nyDeltaker = ulesteEndringer.any { ulestEndring -> ulestEndring.deltakerId == it.deltaker.id && ulestEndring.erNyDeltaker() },
			erUnderOppfolging = it.deltaker.erUnderOppfolging,
		)
	}

	private fun getVeiledertype(deltakerId: UUID, ansattsVeilederDeltakere: List<VeilederDeltakerDbo>): Veiledertype = ansattsVeilederDeltakere
		.find {
			it.deltakerId == deltakerId
		}?.veilederType
		?: throw IllegalStateException("Deltaker med id $deltakerId mangler fra listen, skal ikke kunne skje!")

	private fun getEndringsmeldinger(deltakerId: UUID, endringsmeldinger: List<EndringsmeldingDbo>): List<Endringsmelding> {
		val endringsmeldingerForDeltaker = endringsmeldinger.filter { it.deltakerId == deltakerId }
		return endringsmeldingerForDeltaker.map { it.toEndringsmelding() }
	}
}
