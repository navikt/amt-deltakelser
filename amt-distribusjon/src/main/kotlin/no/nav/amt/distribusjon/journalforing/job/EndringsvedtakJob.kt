package no.nav.amt.distribusjon.journalforing.job

import no.nav.amt.distribusjon.hendelse.HendelseRepository
import no.nav.amt.distribusjon.journalforing.JournalforingService
import no.nav.amt.distribusjon.journalforing.model.HendelseMedJournalforingstatus
import no.nav.amt.lib.utils.job.JobManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime

class EndringsvedtakJob(
    private val jobManager: JobManager,
    private val hendelseRepository: HendelseRepository,
    private val journalforingService: JournalforingService,
    private val initialDelay: Duration,
    private val jobPeriod: Duration,
    private val gracePeriod: Duration,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    init {
        require(!initialDelay.isNegative) {
            "Initial delay for endringsvedtak-jobb kan ikke være negativ"
        }
        require(!jobPeriod.isNegative && !jobPeriod.isZero) {
            "Jobbperiode for endringsvedtak-jobb må være større enn 0"
        }
        require(!gracePeriod.isNegative) {
            "Grace-periode for endringsvedtak-jobb kan ikke være negativ"
        }
    }

    fun startJob() = jobManager.startJob(
        name = this.javaClass.simpleName,
        initialDelay = initialDelay,
        period = jobPeriod,
    ) {
        journalforEndringsvedtak()
    }

    suspend fun journalforEndringsvedtak() {
        val ikkeJournalforteEndringsvedtak = getIkkeJournalforteHendelser()
            .filter { it.hendelse.erEndringsVedtakSomSkalJournalfores() }

        val endringsvedtakPrDeltaker = ikkeJournalforteEndringsvedtak.groupBy { it.hendelse.deltaker.id }

        endringsvedtakPrDeltaker.forEach { (deltakerId, hendelser) ->
            /*
             * Journalfører kun endringsvedtak for en deltaker hvis den nyeste endringen er eldre enn graceperioden.
             * Dette gjøres for å samle alle endringer gjort innenfor en kort periode slik at de havner i samme brev.
             */
            val nyesteHendelseOpprettet = hendelser.maxBy { it.hendelse.opprettet }
            if (nyesteHendelseOpprettet.hendelse.opprettet.isBefore(LocalDateTime.now() - gracePeriod)) {
                log.info("Behandler hendelser: ${hendelser.map { it.hendelse.id }} endringsvedtak for deltaker med id $deltakerId")
                try {
                    journalforingService.journalforOgDistribuerEndringsvedtak(hendelser)
                } catch (e: Exception) {
                    log.error("Behandling av endringsvedtak for deltaker med id $deltakerId feilet", e)
                }
            } else {
                log.info(
                    "Venter med å behandle endringsvedtak for deltaker $deltakerId (nyeste hendelse: id:${nyesteHendelseOpprettet.hendelse.id})",
                )
            }
        }
        log.info("Ferdig med å behandle ${ikkeJournalforteEndringsvedtak.size} endringsvedtak")
    }

    internal fun getIkkeJournalforteHendelser(): List<HendelseMedJournalforingstatus> {
        val ikkeJournalforte = hendelseRepository.hentIkkeJournalforteHendelser()
        val ikkeDistribuerte = hendelseRepository.hentHendelserSomSkalDistribueresSomBrev()

        return ikkeJournalforte + ikkeDistribuerte
    }
}
