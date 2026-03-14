package no.nav.tiltaksarrangor.service

import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.tiltaksarrangor.api.response.UlestEndringResponse
import no.nav.tiltaksarrangor.melding.forslag.AktivtForslagResponse
import no.nav.tiltaksarrangor.melding.forslag.ForslagService
import no.nav.tiltaksarrangor.melding.forslag.tilAktivtForslagResponse
import no.nav.tiltaksarrangor.model.DeltakelsesmengderDto
import no.nav.tiltaksarrangor.model.Deltaker
import no.nav.tiltaksarrangor.model.DeltakerStatusInternalModel
import no.nav.tiltaksarrangor.model.NavInformasjon
import no.nav.tiltaksarrangor.model.NavVeileder
import no.nav.tiltaksarrangor.model.Veileder
import no.nav.tiltaksarrangor.model.Vurdering
import no.nav.tiltaksarrangor.model.toDto
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.EndringsmeldingDbo
import org.springframework.stereotype.Service

val tiltakMedDeltakelsesmengder = setOf(Tiltakskode.ARBEIDSFORBEREDENDE_TRENING, Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET)

@Service
class DeltakerMapper(
	private val ansattService: AnsattService,
	private val forslagService: ForslagService,
	private val endringsmeldingRepository: EndringsmeldingRepository,
	private val unleashToggle: CommonUnleashToggle,
) {
	fun map(
		deltaker: DeltakerDbo,
		deltakerliste: DeltakerlisteDbo,
		ansatt: AnsattDbo,
		ulesteEndringer: List<UlestEndringResponse> = emptyList(),
	): Deltaker {
		val ansattErVeileder = ansattService.erVeilederForDeltaker(
			deltakerId = deltaker.id,
			deltakerlisteArrangorId = deltakerliste.arrangorId,
			ansattDbo = ansatt,
		)

		val aktiveForslag = forslagService.getAktiveForslag(deltaker.id).map { it.tilAktivtForslagResponse() }

		val endringsmeldinger = if (unleashToggle.erKometMasterForTiltakstype(deltakerliste.tiltakskode) ||
			(deltaker.adressebeskyttet && !ansattErVeileder)
		) {
			emptyList()
		} else {
			endringsmeldingRepository.getEndringsmeldingerForDeltaker(deltaker.id)
		}
		val veiledere = ansattService.getVeiledereForDeltaker(deltaker.id)

		val deltakelsesmengder = if (deltakerliste.tiltakskode in tiltakMedDeltakelsesmengder) {
			deltaker.startdato?.let { deltaker.historikk.toDeltakelsesmengder().periode(it, deltaker.sluttdato) }
				?: deltaker.historikk.toDeltakelsesmengder()
		} else {
			null
		}

		return tilDeltaker(
			deltaker,
			deltakerliste,
			veiledere,
			endringsmeldinger,
			aktiveForslag,
			ansattErVeileder,
			deltakelsesmengder,
			ulesteEndringer,
		)
	}
}

private fun tilDeltaker(
	deltakerDbo: DeltakerDbo,
	deltakerliste: DeltakerlisteDbo,
	veiledere: List<Veileder>,
	endringsmeldinger: List<EndringsmeldingDbo>,
	aktiveForslag: List<AktivtForslagResponse>,
	ansattErVeileder: Boolean,
	deltakelsesmengder: Deltakelsesmengder?,
	ulesteEndringer: List<UlestEndringResponse>,
): Deltaker {
	val adressebeskyttet = deltakerDbo.adressebeskyttet
	val deltaker = Deltaker(
		id = deltakerDbo.id,
		deltakerliste =
			Deltaker.Deltakerliste(
				id = deltakerliste.id,
				startDato = deltakerliste.startDato,
				sluttDato = deltakerliste.sluttDato,
				tiltakskode = deltakerliste.tiltakskode,
				oppstartstype = deltakerliste.oppstartstype,
				pameldingstype = deltakerliste.pameldingstype ?: GjennomforingPameldingType.TRENGER_GODKJENNING,
			),
		fornavn = deltakerDbo.fornavn,
		mellomnavn = deltakerDbo.mellomnavn,
		etternavn = deltakerDbo.etternavn,
		fodselsnummer = deltakerDbo.personident,
		telefonnummer = deltakerDbo.telefonnummer,
		epost = deltakerDbo.epost,
		status =
			DeltakerStatusInternalModel(
				type = deltakerDbo.status,
				endretDato = deltakerDbo.statusOpprettetDato,
				aarsak = deltakerDbo.statusAarsak,
			),
		startDato = deltakerDbo.startdato,
		sluttDato = deltakerDbo.sluttdato,
		deltakelseProsent = deltakerDbo.prosentStilling?.toInt(),
		dagerPerUke = deltakerDbo.dagerPerUke,
		soktInnPa = deltakerliste.navn,
		soktInnDato = deltakerDbo.innsoktDato.atStartOfDay(),
		tiltakskode = deltakerliste.tiltakskode,
		bestillingTekst = deltakerDbo.bestillingstekst,
		innhold = deltakerDbo.innhold?.let {
			if (deltakerDbo.innhold.innhold.isEmpty() && deltakerDbo.innhold.ledetekst == null) {
				null
			} else {
				deltakerDbo.innhold
			}
		},
		fjernesDato = deltakerDbo.skalFjernesDato(),
		navInformasjon =
			NavInformasjon(
				navkontor = deltakerDbo.navKontor,
				navVeileder =
					deltakerDbo.navVeilederId?.let {
						NavVeileder(
							navn = deltakerDbo.navVeilederNavn ?: "",
							epost = deltakerDbo.navVeilederEpost,
							telefon = deltakerDbo.navVeilederTelefon,
						)
					},
			),
		veiledere = veiledere,
		aktiveForslag = aktiveForslag,
		aktiveEndringsmeldinger = endringsmeldinger.filter { it.erAktiv() }.sortedBy { it.sendt }.map { it.toEndringsmelding() },
		historiskeEndringsmeldinger = endringsmeldinger
			.filter { !it.erAktiv() }
			.sortedByDescending { it.sendt }
			.map { it.toEndringsmelding() },
		adresse = if (adressebeskyttet) null else deltakerDbo.getAdresse(deltakerliste),
		gjeldendeVurderingFraArrangor = deltakerDbo.getGjeldendeVurdering(),
		adressebeskyttet = adressebeskyttet,
		kilde = deltakerDbo.kilde ?: Kilde.ARENA,
		historikk = deltakerDbo.historikk,
		deltakelsesmengder = deltakelsesmengder?.let {
			DeltakelsesmengderDto(
				nesteDeltakelsesmengde = it.nesteGjeldende?.toDto(),
				sisteDeltakelsesmengde = it.lastOrNull()?.toDto(),
			)
		},
		ulesteEndringer = ulesteEndringer,
		erManueltDeltMedArrangor = deltakerDbo.erManueltDeltMedArrangor,
		erUnderOppfolging = deltakerDbo.erUnderOppfolging,
	)

	return if (adressebeskyttet && !ansattErVeileder) {
		deltaker.utenPersonligInformasjon()
	} else {
		deltaker
	}
}

fun Deltaker.utenPersonligInformasjon() = this.copy(
	fornavn = "",
	mellomnavn = null,
	etternavn = "",
	fodselsnummer = "",
	telefonnummer = null,
	epost = null,
	deltakelseProsent = null,
	dagerPerUke = null,
	bestillingTekst = null,
	navInformasjon = NavInformasjon(
		navkontor = null,
		navVeileder = null,
	),
	aktiveForslag = emptyList(),
	aktiveEndringsmeldinger = emptyList(),
	historiskeEndringsmeldinger = emptyList(),
	gjeldendeVurderingFraArrangor = null,
	historikk = emptyList(),
)

fun DeltakerDbo.getAdresse(deltakerliste: DeltakerlisteDbo) = if (deltakerliste.skalViseAdresseForDeltaker()) {
	this.adresse?.kontaktadresse?.toAdresse()
		?: this.adresse?.oppholdsadresse?.toAdresse()
		?: this.adresse?.bostedsadresse?.toAdresse()
} else {
	null
}

fun DeltakerDbo.getGjeldendeVurdering(): Vurdering? {
	val gjeldendeVurdering = vurderingerFraArrangor?.maxByOrNull { it.opprettet } ?: return null
	return Vurdering(
		vurderingstype = gjeldendeVurdering.vurderingstype,
		begrunnelse = gjeldendeVurdering.begrunnelse,
		gyldigFra = gjeldendeVurdering.opprettet,
		gyldigTil = null,
	)
}
