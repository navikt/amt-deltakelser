package no.nav.tiltaksarrangor.koordinator.service

import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.koordinator.model.AdminDeltakerliste
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.ArrangorDbo
import no.nav.tiltaksarrangor.service.AnsattService
import no.nav.tiltaksarrangor.service.MetricsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DeltakerlisteAdminService(
	private val ansattService: AnsattService,
	private val deltakerlisteRepository: DeltakerlisteRepository,
	private val arrangorRepository: ArrangorRepository,
	private val metricsService: MetricsService,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun getAlleDeltakerlister(personIdent: String): List<AdminDeltakerliste> {
		val ansatt = getAnsattMedKoordinatorRoller(personIdent)
		val koordinatorHosArrangorer = ansatt.roller.filter { it.rolle == AnsattRolle.KOORDINATOR }.map { it.arrangorId }
		val alleDeltakerlister = deltakerlisteRepository.getDeltakerlisterMedArrangor(koordinatorHosArrangorer).filter {
			it.deltakerlisteDbo.erTilgjengeligForArrangor()
		}

		val unikeOverordnedeArrangorIder = alleDeltakerlister.mapNotNull { it.arrangorDbo.overordnetArrangorId }.distinct()
		val overordnedeArrangorer = arrangorRepository.getArrangorer(unikeOverordnedeArrangorIder)

		return alleDeltakerlister.map {
			AdminDeltakerliste(
				id = it.deltakerlisteDbo.id,
				navn = it.deltakerlisteDbo.navn,
				tiltaksnavn = it.deltakerlisteDbo.tiltaksnavn,
				arrangorNavn =
					it.arrangorDbo.overordnetArrangorId?.let { overordnetArrangorId ->
						finnOverordnetArrangorNavn(overordnetArrangorId, overordnedeArrangorer)
					} ?: it.arrangorDbo.navn,
				arrangorOrgnummer = it.arrangorDbo.organisasjonsnummer,
				// arrangørnavn og overordnet arrangørnavn er byttet om i frontend, frem til det fikses der må disse være motsatt av hva som er logisk
				arrangorParentNavn = it.arrangorDbo.navn,
				startDato = it.deltakerlisteDbo.startDato,
				sluttDato = it.deltakerlisteDbo.sluttDato,
				lagtTil =
					ansatt.deltakerlister.find { koordinatorDeltakerliste ->
						koordinatorDeltakerliste.deltakerlisteId == it.deltakerlisteDbo.id
					} != null,
				oppstartstype = it.deltakerlisteDbo.oppstartstype,
			)
		}
	}

	fun leggTilDeltakerliste(deltakerlisteId: UUID, personIdent: String) {
		val ansatt = getAnsattMedKoordinatorRoller(personIdent)
		val deltakerliste =
			deltakerlisteRepository.getDeltakerliste(deltakerlisteId)?.takeIf { it.erTilgjengeligForArrangor() }
				?: throw NoSuchElementException("Fant ikke deltakerliste med id $deltakerlisteId")

		val harKoordinatorRolleHosArrangor =
			ansattService.harRolleHosArrangor(
				arrangorId = deltakerliste.arrangorId,
				rolle = AnsattRolle.KOORDINATOR,
				roller = ansatt.roller,
			)
		if (harKoordinatorRolleHosArrangor) {
			if (!ansattService.deltakerlisteErLagtTil(ansatt, deltakerlisteId)) {
				ansattService.leggTilDeltakerliste(
					ansattId = ansatt.id,
					deltakerlisteId = deltakerlisteId,
					arrangorId = deltakerliste.arrangorId,
				)
				metricsService.incLagtTilDeltakerliste()
				log.info("Lagt til deltakerliste $deltakerlisteId for ansatt ${ansatt.id}")
			} else {
				log.info("Deltakerliste $deltakerlisteId er allerede lagt til for ansatt ${ansatt.id}")
			}
		} else {
			throw UnauthorizedException("Ansatt ${ansatt.id} har ikke tilgang til deltakerliste med id $deltakerlisteId")
		}
	}

	fun fjernDeltakerliste(deltakerlisteId: UUID, personIdent: String) {
		val ansatt = getAnsattMedKoordinatorRoller(personIdent)
		val deltakerliste =
			deltakerlisteRepository.getDeltakerliste(deltakerlisteId)?.takeIf { it.erTilgjengeligForArrangor() }
				?: throw NoSuchElementException("Fant ikke deltakerliste med id $deltakerlisteId")

		val harKoordinatorRolleHosArrangor =
			ansattService.harRolleHosArrangor(
				arrangorId = deltakerliste.arrangorId,
				rolle = AnsattRolle.KOORDINATOR,
				roller = ansatt.roller,
			)
		if (harKoordinatorRolleHosArrangor) {
			if (ansattService.deltakerlisteErLagtTil(ansatt, deltakerlisteId)) {
				ansattService.fjernDeltakerliste(
					ansattId = ansatt.id,
					deltakerlisteId = deltakerlisteId,
					arrangorId = deltakerliste.arrangorId,
				)
				metricsService.incFjernetDeltakerliste()
				log.info("Fjernet deltakerliste $deltakerlisteId for ansatt ${ansatt.id}")
			} else {
				log.info("Deltakerliste $deltakerlisteId er ikke lagt til for ansatt ${ansatt.id}")
			}
		} else {
			throw UnauthorizedException("Ansatt ${ansatt.id} har ikke tilgang til deltakerliste med id $deltakerlisteId")
		}
	}

	private fun getAnsattMedKoordinatorRoller(personIdent: String): AnsattDbo {
		val ansatt = ansattService.getAnsatt(personIdent) ?: throw UnauthorizedException("Ansatt finnes ikke")
		if (!ansattService.erKoordinator(ansatt.roller)) {
			throw UnauthorizedException("Ansatt ${ansatt.id} er ikke koordinator hos noen arrangører")
		}
		return ansatt
	}

	private fun finnOverordnetArrangorNavn(overordnetArrangorId: UUID, overordnedeArrangorer: List<ArrangorDbo>): String? =
		overordnedeArrangorer
			.find {
				it.id == overordnetArrangorId
			}?.navn
}
