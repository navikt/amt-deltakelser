package no.nav.amt.deltaker.job

import io.ktor.util.Attributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringService
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.utils.leaderelection.LeaderElectionClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class DeltakelsesmengdeUpdateJob(
    private val leaderElection: LeaderElectionClient,
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

    // Intern slik at batchløkken kan testes uten å starte timeren.
    internal fun behandleDeltakelsesmengder() {
        var antallBehandlet = 0
        log.info("Starter jobb for å behandle deltakelsesmengder")

        while (true) {
            // Behandlede rader fjernes fra køen, så neste batch hentes alltid uten offset.
            val endringer = deltakerEndringRepository.getUbehandletDeltakelsesmengder()
            if (endringer.isEmpty()) break

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
            antallBehandlet += endringer.size
        }

        log.info("Fullførte jobb for å behandle deltakelsesmengder, behandlet $antallBehandlet endringer")
    }
}
