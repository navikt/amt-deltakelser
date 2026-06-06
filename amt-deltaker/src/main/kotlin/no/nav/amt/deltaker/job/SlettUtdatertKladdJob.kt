package no.nav.amt.deltaker.job

import io.ktor.util.Attributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.amt.deltaker.job.leaderelection.LeaderElection
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.veileder.KladdService
import no.nav.amt.lib.ktor.routing.isReadyKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class SlettUtdatertKladdJob(
    private val leaderElection: LeaderElection,
    private val attributes: Attributes,
    private val deltakerRepository: DeltakerRepository,
    private val kladdService: KladdService,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    fun startJob(): Timer = fixedRateTimer(
        name = this.javaClass.simpleName,
        initialDelay = Duration.of(5, ChronoUnit.MINUTES).toMillis(),
        period = Duration.of(1, ChronoUnit.DAYS).toMillis(),
    ) {
        scope.launch {
            if (leaderElection.isLeader() && attributes.getOrNull(isReadyKey) == true) {
                val sistEndretGrense = LocalDateTime.now().minusWeeks(AGE_IN_WEEKS_THREWHOLD)

                try {
                    log.info("Kjører jobb for å slette utdaterte kladder")
                    val kladderSomSkalSlettes = deltakerRepository.getUtdaterteKladder(sistEndretGrense)

                    kladderSomSkalSlettes.forEach { deltakerId -> kladdService.slettKladd(deltakerId) }

                    log.info("Ferdig med å slette ${kladderSomSkalSlettes.size} kladder")
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved sletting av utdaterte kladder", e)
                }
            }
        }
    }

    companion object {
        private const val AGE_IN_WEEKS_THREWHOLD = 2L
    }
}
