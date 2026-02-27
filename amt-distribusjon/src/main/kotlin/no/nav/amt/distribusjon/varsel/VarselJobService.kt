package no.nav.amt.distribusjon.varsel

import no.nav.amt.lib.utils.job.JobManager
import java.time.Duration

class VarselJobService(
    private val jobManager: JobManager,
    private val varselService: VarselService,
) {
    fun startJobs() {
        sendVentendeVarslerJob()
        revarslingJob()
    }

    private fun sendVentendeVarslerJob() = jobManager.startJob(
        name = "SendVentendeVarslerJob",
        initialDelay = Duration.ofMinutes(2),
        period = Duration.ofMinutes(5),
    ) {
        varselService.sendVentendeVarsler()
    }

    private fun revarslingJob() = jobManager.startJob(
        name = "RevarslingJob",
        initialDelay = Duration.ofHours(1),
        period = Duration.ofHours(1),
    ) {
        varselService.sendRevarsler()
    }
}
