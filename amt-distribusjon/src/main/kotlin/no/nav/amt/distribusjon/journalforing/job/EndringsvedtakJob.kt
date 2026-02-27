package no.nav.amt.distribusjon.journalforing.job

import no.nav.amt.distribusjon.hendelse.HendelseRepository
import no.nav.amt.distribusjon.journalforing.JournalforingService
import no.nav.amt.distribusjon.journalforing.model.HendelseMedJournalforingstatus
import no.nav.amt.lib.utils.job.JobManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class EndringsvedtakJob(
    private val jobManager: JobManager,
    private val hendelseRepository: HendelseRepository,
    private val journalforingService: JournalforingService,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    fun startJob() = jobManager.startJob(
        name = this.javaClass.simpleName,
        initialDelay = Duration.of(5, ChronoUnit.MINUTES),
        period = Duration.of(10, ChronoUnit.MINUTES),
    ) {
        journalforEndringsvedtak()
    }

    suspend fun journalforEndringsvedtak() {
        val ikkeJournalforteEndringsvedtak = getIkkeJournalforteHendelser()
            .filter { it.hendelse.erEndringsVedtakSomSkalJournalfores() }

        val endringsvedtakPrDeltaker = ikkeJournalforteEndringsvedtak.groupBy { it.hendelse.deltaker.id }
        val graceperiode = Duration.ofMinutes(30)

        endringsvedtakPrDeltaker.forEach { (deltakerId, hendelser) ->
            /*
             * Journalfører kun endringsvedtak for en deltaker hvis den nyeste endringen er eldre enn en graceperiode på 30 minutter.
             * Dette gjøres for å samle alle endringer gjort innenfor en kort periode slik at de havner i samme brev.
             */
            val nyesteHendelseOpprettet = hendelser.maxBy { it.hendelse.opprettet }
            if (nyesteHendelseOpprettet.hendelse.opprettet.isBefore(LocalDateTime.now() - graceperiode)) {
                log.info("Behandler hendelser: ${hendelser.map { it.hendelse.id }} endringsvedtak for deltaker med id $deltakerId")
                try {
                    journalforingService.journalforOgDistribuerEndringsvedtak(hendelser)
                } catch (e: Exception) {
                    log.error("Behandling av endringsvedtak for deltaker med id $deltakerId feilet", e)
                }
            } else {
                log.info("Venter med å behandle endringsvedtak for deltaker $deltakerId (nyeste hendelse: $nyesteHendelseOpprettet)")
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
