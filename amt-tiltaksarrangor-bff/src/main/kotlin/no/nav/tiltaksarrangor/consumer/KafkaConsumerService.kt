package no.nav.tiltaksarrangor.consumer

import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Melding
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.tiltaksarrangor.client.amtperson.AmtPersonClient
import no.nav.tiltaksarrangor.client.amtperson.NavEnhetDto
import no.nav.tiltaksarrangor.consumer.ConsumerUtils.getGjennomforingstypeFromDeltakerJsonPayload
import no.nav.tiltaksarrangor.consumer.model.AVSLUTTENDE_STATUSER
import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import no.nav.tiltaksarrangor.consumer.model.ArrangorDto
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingDto
import no.nav.tiltaksarrangor.consumer.model.NavAnsatt
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import no.nav.tiltaksarrangor.consumer.model.SKJULES_ALLTID_STATUSER
import no.nav.tiltaksarrangor.consumer.model.toAnsattDbo
import no.nav.tiltaksarrangor.consumer.model.toArrangorDbo
import no.nav.tiltaksarrangor.consumer.model.toDeltakerDbo
import no.nav.tiltaksarrangor.consumer.model.toEndringsmeldingDbo
import no.nav.tiltaksarrangor.melding.forslag.ForslagService
import no.nav.tiltaksarrangor.model.DeltakerStatusAarsakJsonDboDto
import no.nav.tiltaksarrangor.model.Oppdatering
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.NavAnsattRepository
import no.nav.tiltaksarrangor.repositories.UlestEndringRepository
import no.nav.tiltaksarrangor.repositories.model.DAGER_AVSLUTTET_DELTAKER_VISES
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.service.NavAnsattService
import no.nav.tiltaksarrangor.service.NavEnhetService
import no.nav.tiltaksarrangor.utils.objectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Component
class KafkaConsumerService(
	private val arrangorRepository: ArrangorRepository,
	private val ansattRepository: AnsattRepository,
	private val deltakerRepository: DeltakerRepository,
	private val endringsmeldingRepository: EndringsmeldingRepository,
	private val forslagService: ForslagService,
	private val navEnhetService: NavEnhetService,
	private val navAnsattService: NavAnsattService,
	private val ulestEndringRepository: UlestEndringRepository,
	private val amtPersonClient: AmtPersonClient,
	private val navAnsattRepository: NavAnsattRepository,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun lagreArrangor(arrangorId: UUID, arrangor: ArrangorDto?) {
		if (arrangor == null) {
			arrangorRepository.deleteArrangor(arrangorId)
			log.info("Slettet arrangør med id $arrangorId")
		} else {
			arrangorRepository.insertOrUpdateArrangor(arrangor.toArrangorDbo())
			log.info("Lagret arrangør med id $arrangorId")
		}
	}

	fun lagreAnsatt(ansattId: UUID, ansatt: AnsattDto?) {
		if (ansatt == null) {
			ansattRepository.deleteAnsatt(ansattId)
			log.info("Slettet ansatt med id $ansattId")
		} else {
			ansattRepository.insertOrUpdateAnsatt(ansatt.toAnsattDbo())
			log.info("Lagret ansatt med id $ansattId")
		}
	}

	fun lagreDeltaker(deltakerId: UUID, deltakerPayloadJson: String?) {
		if (deltakerPayloadJson == null) {
			deltakerRepository.deleteDeltaker(deltakerId)
			log.info("Slettet tombstonet deltaker med id $deltakerId")
			return
		}

		// sjekker at gjennomføringstype er støttet før deserialisering
		val gjennomforingstypeFromJson = getGjennomforingstypeFromDeltakerJsonPayload(deltakerPayloadJson)
		if (gjennomforingstypeFromJson != GjennomforingType.Gruppe.name) {
			log.info("Gjennomføringstype $gjennomforingstypeFromJson er ikke støttet.")
			return
		}

		val deltakerPayload: DeltakerKafkaPayload = objectMapper.readValue(deltakerPayloadJson)

		val lagretDeltaker = deltakerRepository.getDeltaker(deltakerId)
		if (deltakerPayload.skalLagres(lagretDeltaker)) {
			leggTilNavAnsattOgEnhetHistorikk(deltakerPayload)

			if (lagretDeltaker == null) {
				val oppdatertKontaktinformasjon = amtPersonClient
					.hentOppdatertKontaktinfo(deltakerPayload.personalia.personident)
					.getOrDefault(deltakerPayload.personalia.kontaktinformasjon)

				deltakerRepository.insertOrUpdateDeltaker(
					deltakerPayload
						.copy(personalia = deltakerPayload.personalia.copy(kontaktinformasjon = oppdatertKontaktinformasjon))
						.toDeltakerDbo(null),
				)
				lagreNyDeltakerUlestEndring(deltakerPayload, deltakerId)
			} else {
				lagreUlesteMeldinger(deltakerId, deltakerPayload, lagretDeltaker)

				deltakerRepository.insertOrUpdateDeltaker(deltakerPayload.toDeltakerDbo(lagretDeltaker))
			}
			log.info("Lagret deltaker med id $deltakerId")
		} else {
			val antallSlettedeDeltakere = deltakerRepository.deleteDeltaker(deltakerId)
			if (antallSlettedeDeltakere > 0) {
				log.info("Slettet deltaker med id $deltakerId")
			} else {
				log.info("Ignorert deltaker med id $deltakerId")
			}
		}
	}

	fun lagreNyDeltakerUlestEndring(deltakerPayload: DeltakerKafkaPayload, deltakerId: UUID) {
		val vedtak = deltakerPayload.historikk?.filterIsInstance<DeltakerHistorikk.Vedtak>()
		val endringer = deltakerPayload.historikk?.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>()

		when {
			!endringer.isNullOrEmpty() ->
				endringer
					.getNyDeltakerEndringFraTiltakskoordinator()
					?.let {
						lagreNyDeltakerUlestEndringForTiltakskoordinatorEndring(it.endringFraTiltakskoordinator, deltakerId)
					}

			deltakerPayload.historikk == null || vedtak.isNullOrEmpty() -> ulestEndringRepository.insert(
				deltakerId,
				Oppdatering.NyDeltaker(
					opprettetAvNavn = null,
					opprettetAvEnhet = null,
					opprettet = deltakerPayload.innsoktDato,
				),
			)

			else -> vedtak.minBy { it.vedtak.opprettet }.vedtak.let {
				ulestEndringRepository.insert(
					deltakerId,
					Oppdatering.NyDeltaker(
						opprettetAvNavn = navAnsattRepository.get(it.opprettetAv)?.navn,
						opprettetAvEnhet = navEnhetService.hentOpprettEllerOppdaterNavEnhet(it.opprettetAvEnhet).navn,
						opprettet = it.opprettet.toLocalDate(),
					),
				)
			}
		}
	}

	private fun lagreNyDeltakerUlestEndringForTiltakskoordinatorEndring(endring: EndringFraTiltakskoordinator, deltakerId: UUID) {
		val oppdatering = mapToTiltakskoordinatorOppdatering(endring, true)

		if (oppdatering != null) {
			ulestEndringRepository.insert(
				deltakerId,
				oppdatering,
			)
		}
	}

	private fun lagreUlesteMeldinger(
		deltakerId: UUID,
		deltakerPayload: DeltakerKafkaPayload,
		lagretDeltaker: DeltakerDbo,
	) {
		if (deltakerPayload.navVeileder?.id != lagretDeltaker.navVeilederId) {
			ulestEndringRepository.insert(
				deltakerId,
				Oppdatering.NavEndring(
					nyNavVeileder = true,
					navVeilederNavn = deltakerPayload.navVeileder?.navn,
					navVeilederEpost = deltakerPayload.navVeileder?.epost,
					navVeilederTelefonnummer = deltakerPayload.navVeileder?.telefon,
					navEnhet = deltakerPayload.navKontor,
				),
			)
		} else if (deltakerPayload.navKontor != lagretDeltaker.navKontor) {
			lagreOppdateringNavEndring(
				deltaker = lagretDeltaker,
				nyttNavn = deltakerPayload.navVeileder?.navn,
				nyEpost = deltakerPayload.navVeileder?.epost,
				nyttTelefonnummer = deltakerPayload.navVeileder?.telefon,
				nyNavEnhet = deltakerPayload.navKontor,
			)
		}

		val ulesteEndringerFraHistorikk = hentUlesteEndringerFraHistorikk(lagretDeltaker, deltakerPayload)
		ulesteEndringerFraHistorikk.forEach {
			ulestEndringRepository.insert(
				deltakerId,
				it,
			)
		}
	}

	private fun hentUlesteEndringerFraHistorikk(lagretDeltaker: DeltakerDbo, nyDeltaker: DeltakerKafkaPayload): List<Oppdatering> =
		nyDeltaker.historikk
			?.toSet()
			?.minus(lagretDeltaker.historikk.toSet())
			?.mapNotNull { toDeltakerOppdatering(it) }
			?: emptyList()

	private fun toDeltakerOppdatering(historikk: DeltakerHistorikk): Oppdatering? = when (historikk) {
		is DeltakerHistorikk.Endring -> Oppdatering.DeltakelsesEndring(historikk.endring)
		is DeltakerHistorikk.Forslag -> {
			when (historikk.forslag.status) {
				is Forslag.Status.Avvist -> Oppdatering.AvvistForslag(historikk.forslag)
				is Forslag.Status.Godkjent,
				is Forslag.Status.Erstattet,
				is Forslag.Status.Tilbakekalt,
				Forslag.Status.VenterPaSvar,
				-> null
			}
		}

		is DeltakerHistorikk.EndringFraTiltakskoordinator -> {
			mapToTiltakskoordinatorOppdatering(historikk.endringFraTiltakskoordinator, false)
		}

		is DeltakerHistorikk.InnsokPaaFellesOppstart,
		is DeltakerHistorikk.EndringFraArrangor,
		is DeltakerHistorikk.ImportertFraArena,
		is DeltakerHistorikk.Vedtak,
		is DeltakerHistorikk.VurderingFraArrangor,
		-> null
	}

	private fun mapToTiltakskoordinatorOppdatering(endring: EndringFraTiltakskoordinator, erNyDeltaker: Boolean): Oppdatering? =
		when (val e = endring.endring) {
			is EndringFraTiltakskoordinator.DelMedArrangor -> Oppdatering.DeltMedArrangor(
				deltAvNavn = navAnsattService.hentEllerOpprettNavAnsatt(endring.endretAv).navn,
				deltAvEnhet = navEnhetService.hentOpprettEllerOppdaterNavEnhet(endring.endretAvEnhet).navn,
				delt = endring.endret.toLocalDate(),
			)

			is EndringFraTiltakskoordinator.TildelPlass -> Oppdatering.TildeltPlass(
				tildeltPlassAvNavn = navAnsattService.hentEllerOpprettNavAnsatt(endring.endretAv).navn,
				tildeltPlassAvEnhet = navEnhetService.hentOpprettEllerOppdaterNavEnhet(endring.endretAvEnhet).navn,
				tildeltPlass = endring.endret.toLocalDate(),
				erNyDeltaker,
			)

			is EndringFraTiltakskoordinator.SettPaaVenteliste -> null
			is EndringFraTiltakskoordinator.Avslag -> Oppdatering.Avslag(
				endretAv = navAnsattService.hentEllerOpprettNavAnsatt(endring.endretAv).navn,
				endretAvEnhet = navEnhetService.hentOpprettEllerOppdaterNavEnhet(endring.endretAvEnhet).navn,
				aarsak = e.aarsak.toDeltakerStatusAarsak(),
				begrunnelse = e.begrunnelse,
			)
		}

	private fun leggTilNavAnsattOgEnhetHistorikk(deltakerPayload: DeltakerKafkaPayload) {
		if (deltakerPayload.historikk.isNullOrEmpty()) {
			return
		}
		lagreEnheterForHistorikk(deltakerPayload.historikk)
		lagreAnsatteForHistorikk(deltakerPayload.historikk)
	}

	fun lagreEnheterForHistorikk(historikk: List<DeltakerHistorikk>?) {
		historikk
			?.flatMap { it.navEnheter() }
			?.distinct()
			?.forEach { id -> navEnhetService.hentOpprettEllerOppdaterNavEnhet(id) }
	}

	fun lagreAnsatteForHistorikk(historikk: List<DeltakerHistorikk>?) {
		historikk
			?.flatMap { it.navAnsatte() }
			?.distinct()
			?.forEach { id -> navAnsattService.hentEllerOpprettNavAnsatt(id) }
	}

	fun lagreNavAnsatt(navAnsatt: NavAnsatt) {
		navAnsattRepository.upsert(navAnsatt)
		log.info("Lagret nav-ansatt med id ${navAnsatt.id}")
	}

	private fun lagreOppdateringNavEndring(
		deltaker: DeltakerDbo,
		nyttNavn: String?,
		nyEpost: String?,
		nyttTelefonnummer: String?,
		nyNavEnhet: String?,
	) {
		val endretNavn = nyttNavn != deltaker.navVeilederNavn
		val endretEpost = nyEpost != deltaker.navVeilederEpost
		val endretTelefonnummer = nyttTelefonnummer != deltaker.navVeilederTelefon
		val endretNavEnhet = nyNavEnhet != deltaker.navKontor
		val harEndringer = endretNavn || endretEpost || endretTelefonnummer || endretNavEnhet
		if (!harEndringer) {
			return
		}

		ulestEndringRepository.insert(
			deltaker.id,
			Oppdatering.NavEndring(
				nyNavVeileder = false,
				navVeilederNavn = if (endretNavn) nyttNavn else null,
				navVeilederEpost = if (endretEpost) nyEpost else null,
				navVeilederTelefonnummer = if (endretTelefonnummer) nyttTelefonnummer else null,
				navEnhet = if (endretNavEnhet) nyNavEnhet else null,
			),
		)
	}

	fun lagreEndringsmelding(endringsmeldingId: UUID, endringsmeldingDto: EndringsmeldingDto?) {
		if (endringsmeldingDto == null) {
			endringsmeldingRepository.deleteEndringsmelding(endringsmeldingId)
			log.info("Slettet tombstonet endringsmelding med id $endringsmeldingId")
		} else {
			endringsmeldingRepository.insertOrUpdateEndringsmelding(endringsmeldingDto.toEndringsmeldingDbo())
			log.info("Lagret endringsmelding med id $endringsmeldingId")
		}
	}

	private fun DeltakerKafkaPayload.skalLagres(lagretDeltaker: DeltakerDbo?): Boolean {
		if (deltakerliste.tiltak.tiltakskode.erEnkeltplass()) {
			return false
		} else if (status.type in SKJULES_ALLTID_STATUSER) {
			return false
		} else if (status.type == DeltakerStatus.Type.IKKE_AKTUELL && lagretDeltaker == null) {
			return false
		} else if (status.type in AVSLUTTENDE_STATUSER) {
			return harNyligSluttet()
		} else if (status.type == DeltakerStatus.Type.SOKT_INN) {
			return erManueltDeltMedArrangor
		}
		return true
	}

	private fun DeltakerKafkaPayload.harNyligSluttet(): Boolean =
		!LocalDateTime.now().isAfter(status.gyldigFra.plusDays(DAGER_AVSLUTTET_DELTAKER_VISES)) &&
			(sluttdato == null || sluttdato!!.isAfter(LocalDate.now().minusDays(DAGER_AVSLUTTET_DELTAKER_VISES)))

	fun handleMelding(id: UUID, melding: Melding?) {
		if (melding == null) {
			log.warn("Mottok tombstone for melding med id: $id")
			forslagService.delete(id)
			return
		}
		when (melding) {
			is Forslag -> handleForslag(melding)
			is EndringFraArrangor,
			is Vurdering,
			-> {
			}
		}
	}

	private fun handleForslag(forslag: Forslag) {
		when (forslag.status) {
			is Forslag.Status.Avvist,
			is Forslag.Status.Godkjent,
			-> {
				forslagService.delete(forslag.id)
			}

			is Forslag.Status.Erstattet,
			is Forslag.Status.Tilbakekalt,
			Forslag.Status.VenterPaSvar,
			-> {
				log.debug("Håndterer ikke forslag {} med status {}", forslag.id, forslag.status.javaClass.simpleName)
			}
		}
	}

	@Transactional
	fun lagreNavEnhet(id: UUID, enhet: NavEnhetDto) {
		val opprinneligEnhet = navEnhetService.hentEnhet(id)
		if (opprinneligEnhet != null && opprinneligEnhet.navn != enhet.navn) {
			deltakerRepository.oppdaterEnhetsnavnForDeltakere(
				opprinneligEnhetsnavn = opprinneligEnhet.navn,
				nyttEnhetsnavn = enhet.navn,
			)
			log.info("Oppdaterer enhetsnavn for deltakere fra ${opprinneligEnhet.navn} til ${enhet.navn}")
		}

		navEnhetService.upsert(enhet.toModel())
		log.info("Lagret Nav-enhet med id $id")
	}
}

private fun NavEnhetDto.toModel() = NavEnhet(
	id = id,
	enhetsnummer = enhetId,
	navn = navn,
)

fun EndringFraTiltakskoordinator.Avslag.Aarsak.toDeltakerStatusAarsak() = DeltakerStatusAarsakJsonDboDto(
	type = DeltakerStatus.Aarsak.Type.valueOf(this.type.name),
	beskrivelse = this.beskrivelse,
)
