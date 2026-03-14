package no.nav.tiltaksarrangor.service

import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorClient
import no.nav.tiltaksarrangor.client.amtarrangor.dto.OppdaterVeiledereForDeltakerRequest
import no.nav.tiltaksarrangor.client.amtarrangor.dto.VeilederAnsatt
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.consumer.model.toAnsattDbo
import no.nav.tiltaksarrangor.model.Veileder
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattPersonaliaDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerMedDeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.KoordinatorDeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.VeilederForDeltakerDbo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class AnsattService(
	private val amtArrangorClient: AmtArrangorClient,
	private val ansattRepository: AnsattRepository,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun oppdaterOgHentMineRoller(personIdent: String): List<String> {
		val ansatt =
			amtArrangorClient.getAnsatt(personIdent) ?: return emptyList<String>().also {
				log.info("Bruker uten tilganger har logget inn")
			}
		log.info("Hentet ansatt med id ${ansatt.id} fra amt-arrangør")

		ansattRepository.insertOrUpdateAnsatt(ansatt.toAnsattDbo())
		log.info("Lagret eller oppdatert ansatt med id ${ansatt.id}")
		ansattRepository.updateSistInnlogget(ansatt.id)

		return ansatt.arrangorer
			.flatMap { it.roller }
			.map { it.name }
			.distinct()
	}

	fun getAnsatt(personIdent: String): AnsattDbo? = ansattRepository.getAnsatt(personIdent)

	fun getAnsattMedRoller(personIdent: String): AnsattDbo {
		val ansatt = getAnsatt(personIdent) ?: throw UnauthorizedException("Ansatt finnes ikke")
		if (!harRoller(ansatt.roller)) {
			throw UnauthorizedException("Ansatt ${ansatt.id} er ikke veileder eller koordinator hos noen arrangører")
		}
		return ansatt
	}

	fun getVeiledereForDeltaker(deltakerId: UUID): List<Veileder> =
		ansattRepository.getVeiledereForDeltaker(deltakerId).map { it.toVeileder() }

	fun getVeiledereForDeltakere(deltakerIder: List<UUID>): List<Veileder> {
		if (deltakerIder.isEmpty()) {
			return emptyList()
		}
		return ansattRepository.getVeiledereForDeltakere(deltakerIder).map { it.toVeileder() }
	}

	@Transactional
	fun leggTilDeltakerliste(
		ansattId: UUID,
		deltakerlisteId: UUID,
		arrangorId: UUID,
	) {
		ansattRepository.insertKoordinatorDeltakerliste(ansattId = ansattId, deltakerliste = KoordinatorDeltakerlisteDbo(deltakerlisteId))
		amtArrangorClient.leggTilDeltakerlisteForKoordinator(ansattId = ansattId, deltakerlisteId = deltakerlisteId, arrangorId = arrangorId)
	}

	@Transactional
	fun fjernDeltakerliste(
		ansattId: UUID,
		deltakerlisteId: UUID,
		arrangorId: UUID,
	) {
		ansattRepository.deleteKoordinatorDeltakerliste(ansattId = ansattId, deltakerliste = KoordinatorDeltakerlisteDbo(deltakerlisteId))
		amtArrangorClient.fjernDeltakerlisteForKoordinator(ansattId = ansattId, deltakerlisteId = deltakerlisteId, arrangorId = arrangorId)
	}

	@Transactional
	fun tildelVeiledereForDeltaker(
		deltakerId: UUID,
		arrangorId: UUID,
		veiledereForDeltaker: List<VeilederForDeltakerDbo>,
	) {
		val gamleVeiledereForDeltaker = ansattRepository.getVeiledereForDeltaker(deltakerId)
		ansattRepository.updateVeiledereForDeltaker(deltakerId = deltakerId, veiledere = veiledereForDeltaker)
		amtArrangorClient.oppdaterVeilederForDeltaker(
			deltakerId = deltakerId,
			oppdaterVeiledereForDeltakerRequest =
				createOppdaterVeiledereForDeltakerRequest(
					arrangorId = arrangorId,
					nyeVeiledereForDeltaker = veiledereForDeltaker,
					gamleVeiledereForDeltaker =
						gamleVeiledereForDeltaker.map {
							VeilederForDeltakerDbo(it.ansattPersonaliaDbo.id, it.veilederDeltakerDbo.veilederType)
						},
				),
		)
	}

	fun getKoordinatorerForDeltakerliste(deltakerlisteId: UUID, arrangorId: UUID): List<AnsattPersonaliaDbo> =
		ansattRepository.getKoordinatorerForDeltakerliste(deltakerlisteId = deltakerlisteId, arrangorId = arrangorId)

	fun getVeiledereForArrangor(arrangorId: UUID): List<AnsattPersonaliaDbo> = ansattRepository.getVeiledereForArrangor(arrangorId)

	fun erAlleAnsatteVeiledereHosArrangor(ansattIder: List<UUID>, arrangorId: UUID): Boolean {
		if (ansattIder.isEmpty()) {
			return true
		}
		val roller = ansattRepository.getAnsattRolleLister(ansattIder).filter { it.ansattRolleDbo.arrangorId == arrangorId }
		ansattIder.forEach { ansattId ->
			val erVeilederHosArrangor =
				harRolleHosArrangor(arrangorId, AnsattRolle.VEILEDER, roller.filter { it.ansattId == ansattId }.map { it.ansattRolleDbo })
			if (!erVeilederHosArrangor) {
				log.info("Ansatt med id $ansattId er ikke veileder hos arrangør $arrangorId")
				return false
			}
		}
		return true
	}

	fun harRoller(roller: List<AnsattRolleDbo>): Boolean {
		val unikeRoller = roller.map { it.rolle }.distinct()
		return unikeRoller.isNotEmpty()
	}

	fun erKoordinator(roller: List<AnsattRolleDbo>): Boolean {
		val unikeRoller = roller.map { it.rolle }.distinct()
		return unikeRoller.find { it == AnsattRolle.KOORDINATOR } != null
	}

	fun erVeileder(roller: List<AnsattRolleDbo>): Boolean {
		val unikeRoller = roller.map { it.rolle }.distinct()
		return unikeRoller.find { it == AnsattRolle.VEILEDER } != null
	}

	fun harRolleHosArrangor(
		arrangorId: UUID,
		rolle: AnsattRolle,
		roller: List<AnsattRolleDbo>,
	): Boolean = roller.find { it.arrangorId == arrangorId && it.rolle == rolle } != null

	fun harTilgangTilDeltaker(
		deltakerId: UUID,
		deltakerlisteId: UUID,
		deltakerlisteArrangorId: UUID,
		ansattDbo: AnsattDbo,
	): Boolean {
		val erKoordinatorHosArrangor = harRolleHosArrangor(deltakerlisteArrangorId, AnsattRolle.KOORDINATOR, ansattDbo.roller)
		val erVeilederForDeltaker = erVeilederForDeltaker(
			deltakerId = deltakerId,
			deltakerlisteArrangorId = deltakerlisteArrangorId,
			ansattDbo = ansattDbo,
		)

		if (erKoordinatorHosArrangor && ansattDbo.deltakerlister.find { it.deltakerlisteId == deltakerlisteId } != null) {
			return true
		} else if (erVeilederForDeltaker) {
			return true
		}
		return false
	}

	fun erVeilederForDeltaker(
		deltakerId: UUID,
		deltakerlisteArrangorId: UUID,
		ansattDbo: AnsattDbo,
	): Boolean {
		val erVeilederHosArrangor = harRolleHosArrangor(deltakerlisteArrangorId, AnsattRolle.VEILEDER, ansattDbo.roller)
		return erVeilederHosArrangor && ansattDbo.veilederDeltakere.find { it.deltakerId == deltakerId } != null
	}

	fun harTilgangTilEndringsmeldingerOgVurderingForDeltaker(
		deltakerMedDeltakerliste: DeltakerMedDeltakerlisteDbo,
		ansatt: AnsattDbo,
	): Boolean = !deltakerMedDeltakerliste.deltaker.adressebeskyttet ||
		(
			deltakerMedDeltakerliste.deltaker.adressebeskyttet &&
				erVeilederForDeltaker(
					deltakerId = deltakerMedDeltakerliste.deltaker.id,
					deltakerlisteArrangorId = deltakerMedDeltakerliste.deltakerliste.arrangorId,
					ansattDbo = ansatt,
				)
		)

	fun deltakerlisteErLagtTil(ansattDbo: AnsattDbo, deltakerlisteId: UUID): Boolean = ansattDbo.deltakerlister.find {
		it.deltakerlisteId == deltakerlisteId
	} != null

	private fun createOppdaterVeiledereForDeltakerRequest(
		arrangorId: UUID,
		nyeVeiledereForDeltaker: List<VeilederForDeltakerDbo>,
		gamleVeiledereForDeltaker: List<VeilederForDeltakerDbo>,
	): OppdaterVeiledereForDeltakerRequest {
		val veiledereSomFjernes = gamleVeiledereForDeltaker.filter { !nyeVeiledereForDeltaker.contains(it) }
		val veiledereSomLeggesTil = nyeVeiledereForDeltaker.filter { !gamleVeiledereForDeltaker.contains(it) }
		return OppdaterVeiledereForDeltakerRequest(
			arrangorId = arrangorId,
			veilederSomLeggesTil = veiledereSomLeggesTil.map { VeilederAnsatt(ansattId = it.ansattId, type = it.veilederType) },
			veilederSomFjernes = veiledereSomFjernes.map { VeilederAnsatt(ansattId = it.ansattId, type = it.veilederType) },
		)
	}
}
