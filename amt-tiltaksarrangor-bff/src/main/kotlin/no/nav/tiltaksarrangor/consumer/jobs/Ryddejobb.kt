package no.nav.tiltaksarrangor.consumer.jobs

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.model.DAGER_AVSLUTTET_DELTAKER_VISES
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class Ryddejobb(
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val deltakerRepository: DeltakerRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *") // kl 03.00 hver natt
    @SchedulerLock(
        name = "slett-utdaterte-deltakerlister-og-deltakere-job",
        lockAtLeastFor = "10m",
        lockAtMostFor = "4h",
    )
    fun slettUtdaterteDeltakerlisterOgDeltakere() {
        slettUtdaterteDeltakerlisterOgDeltakereInternal()
    }

    internal fun slettUtdaterteDeltakerlisterOgDeltakereInternal() {
        val slettesDato = LocalDate.now().minusDays(DAGER_AVSLUTTET_DELTAKER_VISES)
        val deltakerlisterSomSkalSlettes = deltakerlisteRepository.getDeltakerlisterSomSkalSlettes(slettesDato)
        deltakerlisterSomSkalSlettes.forEach { deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(it) }
        log.info("Slettet ${deltakerlisterSomSkalSlettes.size} deltakerlister med deltakere")

        val deltakereSomSkalSlettes = deltakerRepository.getDeltakereSomSkalSlettes(slettesDato)
        deltakereSomSkalSlettes.forEach { deltakerRepository.deleteDeltaker(it) }
        log.info("Slettet ${deltakereSomSkalSlettes.size} deltakere")
    }
}
