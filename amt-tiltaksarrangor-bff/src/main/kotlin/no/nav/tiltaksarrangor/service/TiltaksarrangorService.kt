package no.nav.tiltaksarrangor.service

import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.tiltaksarrangor.api.request.RegistrerVurderingRequest
import no.nav.tiltaksarrangor.api.response.DeltakerHistorikkResponse
import no.nav.tiltaksarrangor.api.response.UlestEndringResponse
import no.nav.tiltaksarrangor.api.response.toResponse
import no.nav.tiltaksarrangor.melding.MeldingProducer
import no.nav.tiltaksarrangor.model.Deltaker
import no.nav.tiltaksarrangor.model.exceptions.ValidationException
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.UlestEndringRepository
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerMedDeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.STATUSER_SOM_KAN_SKJULES
import no.nav.tiltaksarrangor.utils.toTitleCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

@Component
class TiltaksarrangorService(
	private val ansattService: AnsattService,
	private val metricsService: MetricsService,
	private val deltakerRepository: DeltakerRepository,
	private val deltakerlisteRepository: DeltakerlisteRepository,
	private val auditLoggerService: AuditLoggerService,
	private val tilgangskontrollService: TilgangskontrollService,
	private val navAnsattService: NavAnsattService,
	private val navEnhetService: NavEnhetService,
	private val deltakerMapper: DeltakerMapper,
	private val arrangorRepository: ArrangorRepository,
	private val meldingProducer: MeldingProducer,
	private val ulestEndringRepository: UlestEndringRepository,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun getMineRoller(personIdent: String): List<String> = ansattService.oppdaterOgHentMineRoller(personIdent).also {
		metricsService.incInnloggetAnsatt(roller = it)
	}

	fun getDeltaker(personIdent: String, deltakerId: UUID): Deltaker {
		val ansatt = ansattService.getAnsattMedRoller(personIdent)
		val deltakerMedDeltakerliste =
			deltakerRepository.getDeltakerMedDeltakerliste(deltakerId)?.takeIf { it.deltakerliste.erTilgjengeligForArrangor() }
				?: throw NoSuchElementException("Fant ikke deltaker med id $deltakerId")

		if (!deltakerMedDeltakerliste.deltaker.skalVises()) {
			throw NoSuchElementException("Fant ikke deltaker med id $deltakerId")
		}

		auditLoggerService.sendAuditLog(
			ansattPersonIdent = ansatt.personIdent,
			deltakerPersonIdent = deltakerMedDeltakerliste.deltaker.personident,
			arrangorId = deltakerMedDeltakerliste.deltakerliste.arrangorId,
		)

		tilgangskontrollService.verifiserTilgangTilDeltaker(ansatt, deltakerMedDeltakerliste)

		val ulesteEndringerResponse = getUlesteEndringer(deltakerMedDeltakerliste)

		return deltakerMapper.map(
			deltakerMedDeltakerliste.deltaker,
			deltakerMedDeltakerliste.deltakerliste,
			ansatt,
			ulesteEndringerResponse,
		)
	}

	fun getUlesteEndringer(medDeltakerlisteDbo: DeltakerMedDeltakerlisteDbo): List<UlestEndringResponse> {
		val ulesteEndringer = ulestEndringRepository.getMany(medDeltakerlisteDbo.deltaker.id)
		if (ulesteEndringer.isEmpty()) {
			return emptyList()
		}
		val ansatte = navAnsattService.hentAnsatteForUlesteEndringer(ulesteEndringer)
		val enheter = navEnhetService.hentEnheterForUlesteEndringer(ulesteEndringer)

		val deltakerlisteMedArrangor =
			deltakerlisteRepository
				.getDeltakerlisteMedArrangor(
					medDeltakerlisteDbo.deltakerliste.id,
				)?.takeIf { it.deltakerlisteDbo.erTilgjengeligForArrangor() }
				?: throw NoSuchElementException("Fant ikke deltakerliste med id ${medDeltakerlisteDbo.deltakerliste.id}")

		val overordnetArrangor = deltakerlisteMedArrangor.arrangorDbo.overordnetArrangorId?.let { arrangorRepository.getArrangor(it) }
		val arrangorNavn = overordnetArrangor?.navn ?: deltakerlisteMedArrangor.arrangorDbo.navn
		return ulesteEndringer.toResponse(ansatte, toTitleCase(arrangorNavn), enheter, deltakerlisteMedArrangor.deltakerlisteDbo.oppstartstype)
	}

	fun getDeltakerHistorikk(personIdent: String, deltakerId: UUID): List<DeltakerHistorikkResponse> {
		val deltaker = getDeltaker(personIdent, deltakerId)
		val historikk = deltaker.historikk.sortedByDescending { it.sistEndret }
		if (historikk.isEmpty()) {
			return emptyList()
		}
		val ansatte = navAnsattService.hentAnsatteForHistorikk(historikk)
		val enheter = navEnhetService.hentEnheterForHistorikk(historikk)

		val deltakerlisteMedArrangor =
			deltakerlisteRepository
				.getDeltakerlisteMedArrangor(
					deltaker.deltakerliste.id,
				)?.takeIf { it.deltakerlisteDbo.erTilgjengeligForArrangor() }
				?: throw NoSuchElementException("Fant ikke deltakerliste med id ${deltaker.deltakerliste.id}")

		val overordnetArrangor = deltakerlisteMedArrangor.arrangorDbo.overordnetArrangorId?.let { arrangorRepository.getArrangor(it) }

		val arrangorNavn = overordnetArrangor?.navn ?: deltakerlisteMedArrangor.arrangorDbo.navn
		return historikk
			.filterNot { deltaker.deltakerliste.pameldingstype == GjennomforingPameldingType.TRENGER_GODKJENNING && it is DeltakerHistorikk.Vedtak }
			.toResponse(ansatte, toTitleCase(arrangorNavn), enheter, deltaker.deltakerliste.oppstartstype)
	}

	fun registrerVurdering(
		personIdent: String,
		deltakerId: UUID,
		request: RegistrerVurderingRequest,
	) {
		val ansatt = ansattService.getAnsattMedRoller(personIdent)
		val deltakerMedDeltakerliste =
			deltakerRepository.getDeltakerMedDeltakerliste(deltakerId)?.takeIf { it.deltakerliste.erTilgjengeligForArrangor() }
				?: throw NoSuchElementException("Fant ikke deltaker med id $deltakerId")

		tilgangskontrollService.verifiserTilgangTilDeltakerOgMeldinger(ansatt, deltakerMedDeltakerliste)

		if (!(
				deltakerMedDeltakerliste.deltaker.status == DeltakerStatus.Type.VURDERES ||
					deltakerMedDeltakerliste.deltaker.status == DeltakerStatus.Type.SOKT_INN
			)
		) {
			throw IllegalStateException(
				"Kan ikke registrere vurdering for deltaker med id $deltakerId med annen status enn VURDERES eller SOKT_INN. " +
					"Ugyldig status: ${deltakerMedDeltakerliste.deltaker.status.name}",
			)
		}
		if (request.vurderingstype == Vurderingstype.OPPFYLLER_IKKE_KRAVENE && request.begrunnelse.isNullOrEmpty()) {
			throw ValidationException("Kan ikke registrere vurdering for deltaker med id $deltakerId. Begrunnelse mangler.")
		}

		val vurdering = Vurdering(
			id = UUID.randomUUID(),
			deltakerId = deltakerId,
			opprettetAvArrangorAnsattId = ansatt.id,
			opprettet = LocalDateTime.now(),
			vurderingstype = request.vurderingstype,
			begrunnelse = request.begrunnelse,
		)

		meldingProducer.produce(vurdering)

		val opprinneligeVurderinger = deltakerMedDeltakerliste.deltaker.vurderingerFraArrangor ?: emptyList()
		val oppdaterteVurderinger = listOf(vurdering) + opprinneligeVurderinger
		deltakerRepository.oppdaterVurderingerForDeltaker(deltakerId, oppdaterteVurderinger)
		metricsService.incVurderingOpprettet(request.vurderingstype)
		log.info("Registrert vurdering for deltaker med id $deltakerId")
	}

	fun fjernDeltaker(personIdent: String, deltakerId: UUID) {
		val ansatt = ansattService.getAnsattMedRoller(personIdent)
		val deltakerMedDeltakerliste =
			deltakerRepository.getDeltakerMedDeltakerliste(deltakerId)?.takeIf { it.deltakerliste.erTilgjengeligForArrangor() }
				?: throw NoSuchElementException("Fant ikke deltaker med id $deltakerId")

		tilgangskontrollService.verifiserTilgangTilDeltaker(ansatt, deltakerMedDeltakerliste)

		if (kanSkjules(deltakerMedDeltakerliste.deltaker)) {
			deltakerRepository.skjulDeltaker(deltakerId = deltakerId, ansattId = ansatt.id)
			metricsService.incFjernetDeltaker()
			log.info("Skjult deltaker med id $deltakerId")
		} else {
			throw IllegalStateException(
				"Kan ikke skjule deltaker med id $deltakerId. Ugyldig status: ${deltakerMedDeltakerliste.deltaker.status.name}",
			)
		}
	}

	private fun kanSkjules(deltakerDbo: DeltakerDbo): Boolean = deltakerDbo.status in STATUSER_SOM_KAN_SKJULES

	fun markerEndringSomLest(
		personIdent: String,
		deltakerId: UUID,
		ulestEndringId: UUID,
	) {
		val ansatt = ansattService.getAnsattMedRoller(personIdent)
		val deltakerMedDeltakerliste =
			deltakerRepository.getDeltakerMedDeltakerliste(deltakerId)?.takeIf { it.deltakerliste.erTilgjengeligForArrangor() }
				?: throw NoSuchElementException("Fant ikke deltaker med id $deltakerId")

		tilgangskontrollService.verifiserTilgangTilDeltaker(ansatt, deltakerMedDeltakerliste)
		ulestEndringRepository.delete(ulestEndringId)
	}
}
