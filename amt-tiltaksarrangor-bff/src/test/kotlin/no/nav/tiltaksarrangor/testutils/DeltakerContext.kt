package no.nav.tiltaksarrangor.testutils

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.tiltaksarrangor.consumer.model.NavAnsatt
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.NavAnsattRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.ArrangorDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.KoordinatorDeltakerlisteDbo
import no.nav.tiltaksarrangor.testutils.DbTestDataUtils.loggerFor
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeansOfType
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDateTime
import java.util.UUID

open class DeltakerContext(
	val applicationContext: ApplicationContext,
	val navVeileder: NavAnsatt = getNavAnsatt(),
	var deltaker: DeltakerDbo = getDeltaker(deltakerId = UUID.randomUUID(), navVeilederId = navVeileder.id),
	val arrangor: ArrangorDbo = getArrangor(),
	val deltakerliste: DeltakerlisteDbo = getDeltakerliste(deltaker.deltakerlisteId, arrangorId = arrangor.id),
	val koordinator: AnsattDbo = getKoordinator(
		id = UUID.randomUUID(),
		arrangorId = arrangor.id,
		deltakerlisteId = deltakerliste.id,
	),
	val veileder: AnsattDbo = getVeileder(
		id = UUID.randomUUID(),
		arrangorId = arrangor.id,
		deltakerId = deltaker.id,
	),
) {
	val log = loggerFor<DeltakerContext>()

	protected inline fun <reified T : Any> getOrCreateBean(creator: (NamedParameterJdbcTemplate) -> T): T =
		applicationContext.getBeansOfType<T>().values.firstOrNull()?.apply {
			log.debug("Found bean of type {}", T::class.simpleName)
		} ?: creator(applicationContext.getBean<NamedParameterJdbcTemplate>()).apply {
			log.debug("No beans of type {} found, created new instance", T::class.simpleName)
		}

	val deltakerRepository = getOrCreateBean { template -> DeltakerRepository(template) }
	private val deltakerlisteRepository = getOrCreateBean { template -> DeltakerlisteRepository(template, deltakerRepository) }
	private val ansattRepository = getOrCreateBean { template -> AnsattRepository(template) }
	private val arrangorRepository = getOrCreateBean { template -> ArrangorRepository(template) }
	private val endringsmeldingRepository = getOrCreateBean { template -> EndringsmeldingRepository(template) }
	private val navAnsattRepository = getOrCreateBean { template -> NavAnsattRepository(template) }

	init {
		arrangorRepository.insertOrUpdateArrangor(arrangor)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		ansattRepository.insertOrUpdateAnsatt(koordinator)
		ansattRepository.insertOrUpdateAnsatt(veileder)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		navAnsattRepository.upsert(navVeileder)
	}

	fun setKoordinatorDeltakerliste(id: UUID) {
		ansattRepository.insertOrUpdateAnsatt(koordinator.copy(deltakerlister = listOf(KoordinatorDeltakerlisteDbo(id))))
	}

	fun setDeltakerAdressebeskyttet() {
		deltakerRepository.insertOrUpdateDeltaker(deltaker.copy(adressebeskyttet = true))
	}

	fun setDeltakerSkjult() {
		deltakerRepository.insertOrUpdateDeltaker(deltaker.copy(skjultDato = LocalDateTime.now(), skjultAvAnsattId = koordinator.id))
	}

	fun medPersonident(ident: String) {
		deltaker = deltaker.copy(personident = ident)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
	}

	fun medStatus(status: DeltakerStatus.Type, gyldigFraDagerSiden: Long = 1L) {
		deltaker = deltaker.copy(
			status = status,
			statusGyldigFraDato = LocalDateTime.now().minusDays(gyldigFraDagerSiden),
			statusOpprettetDato = LocalDateTime.now().minusDays(gyldigFraDagerSiden),
		)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
	}

	fun medEndringsmelding() {
		endringsmeldingRepository.insertOrUpdateEndringsmelding(
			getEndringsmelding(
				deltaker.id,
			),
		)
	}

	fun setVenterPaOppstart() {
		deltaker = deltaker.copy(
			startdato = null,
			sluttdato = null,
			status = DeltakerStatus.Type.VENTER_PA_OPPSTART,
		)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
	}
}
