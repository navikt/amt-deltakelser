package no.nav.tiltaksarrangor.consumer.jobs

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.tiltaksarrangor.client.amtperson.AmtPersonClient
import no.nav.tiltaksarrangor.repositories.DeltakerKontaktinfoRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class OppdaterKontaktinfoJob(
	private val repository: DeltakerKontaktinfoRepository,
	private val amtPersonClient: AmtPersonClient,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	@Scheduled(cron = "0 5 * * * *") // 5 minutes past each hour
	@SchedulerLock(
		name = "oppdater-kontaktinformasjon-job",
		lockAtLeastFor = "15m",
		lockAtMostFor = "50m",
	)
	fun oppdaterKontaktinformasjon() {
		val currentHour = LocalDateTime.now().hour
		val personidenterForOppdatering = repository.getPersonerForOppdatering(currentHour)
		log.info("Oppdaterer kontaktinformasjon for ${personidenterForOppdatering.size} deltakere for time $currentHour")

		personidenterForOppdatering.chunked(250).forEach {
			amtPersonClient
				.hentOppdatertKontaktinfo(it.toSet())
				.onSuccess { personIdentTilKontaktInfoMap -> repository.oppdaterKontaktinformasjon(personIdentTilKontaktInfoMap) }
				.onFailure { error ->
					log.error("Feil ved oppdatering av kontaktinformasjon for deltakere: ${error.message}")
				}
		}

		log.info("Ferdig med oppdatering av kontaktinformasjon for time $currentHour")
	}
}
