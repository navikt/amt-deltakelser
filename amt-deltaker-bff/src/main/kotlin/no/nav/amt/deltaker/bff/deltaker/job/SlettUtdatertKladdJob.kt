package no.nav.amt.deltaker.bff.deltaker.job

import io.ktor.util.Attributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.job.leaderelection.LeaderElection
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
    private val pameldingService: PameldingService,
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
                val sistEndretGrense = LocalDateTime.now().minusWeeks(2)
                try {
                    log.info("Kjører jobb for å slette utdaterte kladder")
                    val kladderSomSkalSlettes = deltakerRepository.getUtdaterteKladder(sistEndretGrense)
                    kladderSomSkalSlettes.forEach {
                        pameldingService.slettKladd(it)
                    }
                    log.info("Ferdig med å slette ${kladderSomSkalSlettes.size} kladder")
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved sletting av utdaterte kladder", e)
                }
            }
        }
    }
}
