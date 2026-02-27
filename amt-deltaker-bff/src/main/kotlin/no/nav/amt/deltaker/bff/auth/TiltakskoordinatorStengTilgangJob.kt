package no.nav.amt.deltaker.bff.auth

import io.ktor.util.Attributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.amt.deltaker.bff.deltaker.job.leaderelection.LeaderElection
import no.nav.amt.lib.ktor.routing.isReadyKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class TiltakskoordinatorStengTilgangJob(
    private val leaderElection: LeaderElection,
    private val attributes: Attributes,
    private val tilgangskontrollService: TilgangskontrollService,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startJob(): Timer = fixedRateTimer(
        name = this.javaClass.simpleName,
        initialDelay = Duration.of(5, ChronoUnit.MINUTES).toMillis(),
        period = Duration.of(1, ChronoUnit.DAYS).toMillis(),
    ) {
        scope.launch {
            if (leaderElection.isLeader() && attributes.getOrNull(isReadyKey) == true) {
                try {
                    log.info("Kjører jobb for å stenge tilganger til avsluttede deltakerlister")
                    val tilgangerSomskalStenges = tilgangskontrollService.getUtdaterteTiltakskoordinatorTilganger()
                    tilgangerSomskalStenges.forEach {
                        tilgangskontrollService.stengTiltakskoordinatorTilgang(it.id)
                    }
                    log.info("Ferdig med å stenge ${tilgangerSomskalStenges.size} tilganger")
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved stenging av utdaterte tilganger", e)
                }
            }
        }
    }
}
