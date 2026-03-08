package no.nav.amt.lib.utils.job

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages recurring jobs in a coroutine-based environment.
 *
 * This class is responsible for starting, stopping, and managing the lifecycle of recurring jobs.
 * It ensures that jobs are only run when the application instance is the leader and is ready to handle them.
 *
 * @param isLeader A function that returns `true` if the current instance is the leader.
 * @param applicationIsReady A function that returns `true` if the application is ready to execute jobs.
 */
class JobManager(
    private val isLeader: suspend () -> Boolean,
    private val applicationIsReady: () -> Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = CopyOnWriteArrayList<Job>()
    private var isRunning = true

    /**
     * Starts a new recurring job.
     *
     * The job will be executed repeatedly at a fixed rate, but only if the current instance is the leader
     * and the application is ready. If a job's execution takes longer than the specified period, the next
     * execution will start immediately after the current one finishes.
     *
     * @param name The name of the job, used for logging.
     * @param initialDelay The delay before the first execution of the job.
     * @param period The time period between the start of each job execution.
     * @param job The suspend function to be executed.
     */
    fun startJob(
        name: String,
        initialDelay: Duration,
        period: Duration,
        job: suspend () -> Unit,
    ) {
        scope
            .launch {
                delay(initialDelay.toMillis())
                while (isRunning) {
                    val startTime = System.currentTimeMillis()
                    if (isLeader() && applicationIsReady()) {
                        try {
                            if (period > Duration.ofMinutes(5)) {
                                // Ikke logg for jobber som kjører oftere enn hver 5. minutt (blir unødvendig støy)
                                log.info("Kjører jobb: $name")
                            }
                            job()
                        } catch (e: CancellationException) {
                            log.info("Jobb $name ble avbrutt")
                            break
                        } catch (e: Exception) {
                            log.error("Noe gikk galt med jobb: $name", e)
                        }
                    }
                    val executionTime = System.currentTimeMillis() - startTime
                    val delayTime = (period.toMillis() - executionTime).coerceAtLeast(0)
                    delay(delayTime)
                }
                log.info("Jobb $name er stoppet")
            }.also { jobs.add(it) }
    }

    /**
     * Stops all jobs that have been started by this JobManager.
     *
     * This function gracefully stops all running jobs by cancelling them and waiting for them to finish.
     */
    suspend fun stopJobs() {
        log.info("Stopping all jobs...")
        isRunning = false
        jobs.forEach { it.cancelAndJoin() }
        log.info("All jobs have been stopped.")
    }
}
