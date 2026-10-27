package no.nav.amt.distribusjon.journalforing.job

import no.nav.amt.distribusjon.hendelse.HendelseRepository
import no.nav.amt.distribusjon.journalforing.JournalforingService
import no.nav.amt.distribusjon.journalforing.JournalforingstatusRepository
import no.nav.amt.distribusjon.journalforing.model.HendelseMedJournalforingstatus
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.utils.job.JobManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

class EndringsvedtakJob(
    private val jobManager: JobManager,
    private val hendelseRepository: HendelseRepository,
    private val journalforingstatusRepository: JournalforingstatusRepository,
    private val journalforingService: JournalforingService,
    private val initialDelay: Duration,
    private val jobPeriod: Duration,
    private val gracePeriod: Duration,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    init {
        require(initialDelay.isPositive) {
            "Initial delay for endringsvedtak-jobb må være større enn 0"
        }
        require(jobPeriod.isPositive) {
            "Jobbperiode for endringsvedtak-jobb må være større enn 0"
        }
        require(gracePeriod.isPositive) {
            "Grace-periode for endringsvedtak-jobb må være større enn 0"
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
        val hendelser = getIkkeJournalforteHendelser()
            .filter { it.hendelse.erEndringSomSkalJournalfores() }

        // Grupper hendelser på deltaker-ID
        val endringerPerDeltaker = hendelser.groupBy { it.hendelse.deltaker.id }

        // Bare sendte hovedvedtak gir en fattetDato vi kan bruke som grense.
        // Eldre hovedvedtak uten fattetDato kan ikke brukes.
        val fattetDatoPerDeltaker = hendelseRepository
            .hentDistribuerteEnkeltplassHovedvedtak(endringerPerDeltaker.keys)
            .mapNotNull { okonomiGodkjent ->
                (okonomiGodkjent.hendelse.payload as? HendelseType.EnkeltplassOkonomiGodkjennUtkast)
                    ?.hovedvedtakFattetDato
                    ?.let { okonomiGodkjent.hendelse.deltaker.id to it }
            }.toMap()

        var antallAnnullert = 0
        endringerPerDeltaker.forEach { (deltakerId, hendelser) ->
            // Denne flyten gir null eller ett sendt hovedvedtak per deltaker.
            val fattetDato = fattetDatoPerDeltaker[deltakerId]

            val (skalBehandles, antallAnnullertForDeltaker) = if (fattetDato != null) {
                val annullering = annullerDekkedeEndringsvedtak(
                    deltakerId = deltakerId,
                    hendelser = hendelser,
                    fattetDato = fattetDato,
                )
                annullering.skalBehandles to annullering.antallAnnullert
            } else {
                hendelser to 0
            }

            antallAnnullert += antallAnnullertForDeltaker

            if (skalBehandles.isEmpty()) {
                return@forEach
            }

            /*
             * Vent til det har gått mer enn gracePeriod siden den nyeste endringen.
             * Da samler jobben endringer som kom tett på hverandre i samme brev.
             */
            val nyesteHendelseOpprettet = skalBehandles.maxBy { it.hendelse.opprettet }
            if (nyesteHendelseOpprettet.hendelse.opprettet.isBefore(LocalDateTime.now() - gracePeriod)) {
                log.info(
                    "Behandler endringsvedtak med hendelses-ID-er ${skalBehandles.map { it.hendelse.id }} for deltaker $deltakerId",
                )
                try {
                    journalforingService.journalforOgDistribuerEndringsvedtak(skalBehandles)
                } catch (e: Exception) {
                    log.error("Behandling av endringsvedtak for deltaker med id $deltakerId feilet", e)
                }
            } else {
                log.info(
                    "Venter med å behandle endringsvedtak for deltaker $deltakerId (nyeste hendelse: id:${nyesteHendelseOpprettet.hendelse.id})",
                )
            }
        }
        log.info("Ferdig med å behandle ${hendelser.size - antallAnnullert} endringsvedtak og annullere $antallAnnullert")
    }

    /**
     * Annullerer endringer som et hovedvedtak dekker og returnerer resten for videre behandling.
     *
     * @param deltakerId ID-en til deltakeren endringene gjelder.
     * @param hendelser Endringene som venter på behandling.
     * @param fattetDato Tidspunktet hovedvedtaket ble fattet, eller null hvis det ikke finnes.
     * @return Endringene som skal behandles videre og antall statuser som ble annullert.
     */
    private fun annullerDekkedeEndringsvedtak(
        deltakerId: UUID,
        hendelser: List<HendelseMedJournalforingstatus>,
        fattetDato: LocalDateTime,
    ): AnnulleringResultat {
        val (hendelserDekketAvHovedvedtak, skalBehandles) = hendelser.partition {
            it.hendelse.opprettet <= fattetDato
        }

        if (hendelserDekketAvHovedvedtak.isEmpty()) {
            return AnnulleringResultat(
                skalBehandles = skalBehandles,
                antallAnnullert = 0,
            )
        }

        // Lagre annulleringen før vi behandler senere endringer.
        // Statusen hindrer at annullerte endringer dukker opp igjen.
        val annullerte = journalforingstatusRepository.annuller(
            hendelserDekketAvHovedvedtak.map { it.hendelse.id },
        )

        log.info(
            "Annullerte $annullerte av ${hendelserDekketAvHovedvedtak.size} endringsvedtak " +
                "dekket av hovedvedtak for deltaker $deltakerId",
        )

        return AnnulleringResultat(
            skalBehandles = skalBehandles,
            antallAnnullert = annullerte,
        )
    }

    internal fun getIkkeJournalforteHendelser(): List<HendelseMedJournalforingstatus> {
        val ikkeJournalforte = hendelseRepository.hentIkkeJournalforteHendelser()
        val ikkeDistribuerte = hendelseRepository.hentHendelserSomSkalDistribueresSomBrev()

        return ikkeJournalforte + ikkeDistribuerte
    }

    private data class AnnulleringResultat(
        val skalBehandles: List<HendelseMedJournalforingstatus>,
        val antallAnnullert: Int,
    )
}
