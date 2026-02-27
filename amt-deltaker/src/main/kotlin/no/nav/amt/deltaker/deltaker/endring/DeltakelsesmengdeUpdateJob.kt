package no.nav.amt.deltaker.deltaker.endring

import io.ktor.util.Attributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.job.leaderelection.LeaderElection
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class DeltakelsesmengdeUpdateJob(
    private val leaderElection: LeaderElection,
    private val attributes: Attributes,
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val deltakerEndringService: DeltakerEndringService,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startJob(): Timer = fixedRateTimer(
        name = this.javaClass.simpleName,
        initialDelay = Duration.of(5, ChronoUnit.MINUTES).toMillis(),
        period = Duration.of(1, ChronoUnit.HOURS).toMillis(),
    ) {
        scope.launch {
            if (leaderElection.isLeader() && attributes.getOrNull(isReadyKey) == true) {
                behandleDeltakelsesmengder()
            }
        }
    }

    private suspend fun behandleDeltakelsesmengder() {
        var offset = 0
        var endringer: List<DeltakerEndring>

        log.info("Starter jobb for å behandle deltakelsesmengder")

        do {
            endringer = deltakerEndringRepository.getUbehandletDeltakelsesmengder(offset)
            endringer.forEach { deltakerEndring ->
                val opprinneligDeltaker = deltakerRepository.get(deltakerEndring.deltakerId).getOrThrow()

                deltakerEndringService
                    .behandleLagretDeltakelsesmengde(
                        deltakerEndring = deltakerEndring,
                        deltaker = opprinneligDeltaker,
                    ).onSuccess { vellykketEndring ->
                        deltakerService.upsertAndProduceDeltaker(
                            deltaker = vellykketEndring.deltaker,
                            erDeltakerSluttdatoEndret = opprinneligDeltaker.sluttdato != vellykketEndring.deltaker.sluttdato,
                        )
                    }
            }
            offset += endringer.size
        } while (endringer.isNotEmpty())

        log.info("Fullførte jobb for å behandle deltakelsesmengder, behandlet $offset endringer")
    }
}
