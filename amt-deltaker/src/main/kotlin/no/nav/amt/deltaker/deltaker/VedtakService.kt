package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class VedtakService(
    private val vedtakRepository: VedtakRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun avbrytVedtak(
        deltakerId: UUID,
        avbruttAv: NavAnsatt,
        avbruttAvNavEnhet: NavEnhet,
    ): Vedtak {
        val vedtak = hentIkkeFattetVedtakOrThrow(deltakerId)

        return vedtakRepository.upsert(
            vedtak.copy(
                gyldigTil = LocalDateTime.now(),
                sistEndret = LocalDateTime.now(),
                sistEndretAv = avbruttAv.id,
                sistEndretAvEnhet = avbruttAvNavEnhet.id,
            ),
        )
    }

    fun avbrytVedtakVedAvsluttetDeltakerliste(deltaker: Deltaker): Vedtak {
        val vedtak = vedtakRepository.getForDeltaker(deltaker.id)
            ?: throw IllegalStateException("Deltaker ${deltaker.id} har ikke vedtak som kan avbrytes")

        val avbruttVedtak = vedtak.copy(
            gyldigTil = LocalDateTime.now(),
        )

        return vedtakRepository.upsert(avbruttVedtak)
    }

    fun navFattVedtak(
        deltaker: Deltaker,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
    ): Vedtak {
        val vedtak = vedtakRepository.getForDeltaker(deltaker.id)
            ?: throw IllegalStateException("Deltaker ${deltaker.id} mangler et vedtak som kan fattes")

        if (vedtak.fattet != null) {
            log.info("Vedtak allerede fattet for deltaker ${deltaker.id}, fatter ikke nytt vedtak")
            return vedtak
        }

        log.info("Fatter hovedvedtak for deltaker ${deltaker.id}")
        return opprettEllerOppdaterVedtak(
            fattetAvNav = true,
            endretAv = endretAv,
            endretAvEnhet = endretAvEnhet,
            deltaker = deltaker.toDeltakerVedVedtak(),
            fattetDato = LocalDateTime.now(),
        )
    }

    /**
     Kan bare brukes når deltaker selv godkjenner utkast.
     Hvis Nav fatter vedtaket må [opprettEllerOppdaterVedtak] brukes.
     */
    fun innbyggerFattVedtak(deltakerId: UUID) {
        val ikkeFattetVedtak = hentIkkeFattetVedtakOrThrow(deltakerId)
        val fattetVedtak = ikkeFattetVedtak.copy(fattet = LocalDateTime.now())

        vedtakRepository.upsert(fattetVedtak)
    }

    private fun hentIkkeFattetVedtakOrThrow(deltakerId: UUID): Vedtak {
        val vedtak = vedtakRepository.getForDeltaker(deltakerId)

        when {
            vedtak == null -> throw IllegalStateException("Deltaker-id $deltakerId har ingen vedtak")
            vedtak.gyldigTil != null -> throw IllegalStateException("Deltaker-id $deltakerId har et vedtak som er avbrutt")
            vedtak.fattet != null -> throw IllegalArgumentException("Deltaker-id $deltakerId har allerede et fattet vedtak")
        }

        return vedtak
    }

    fun opprettEllerOppdaterVedtak(
        fattetAvNav: Boolean,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
        deltaker: DeltakerVedVedtak,
        fattetDato: LocalDateTime?,
    ): Vedtak {
        val eksisterendeVedtak = vedtakRepository.getForDeltaker(deltaker.id)

        val oppdatertVedtak = Vedtak(
            id = eksisterendeVedtak?.id ?: UUID.randomUUID(),
            deltakerId = deltaker.id,
            fattet = fattetDato,
            gyldigTil = null,
            deltakerVedVedtak = deltaker,
            fattetAvNav = fattetAvNav,
            opprettetAv = eksisterendeVedtak?.opprettetAv ?: endretAv.id,
            opprettetAvEnhet = eksisterendeVedtak?.opprettetAvEnhet ?: endretAvEnhet.id,
            opprettet = eksisterendeVedtak?.opprettet ?: LocalDateTime.now(),
            sistEndretAv = endretAv.id,
            sistEndretAvEnhet = endretAvEnhet.id,
            sistEndret = LocalDateTime.now(),
        )

        return vedtakRepository.upsert(oppdatertVedtak)
    }
}
