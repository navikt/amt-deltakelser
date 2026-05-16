package no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse

import no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions.toUlestHendelse
import no.nav.amt.lib.models.hendelse.Hendelse
import org.slf4j.LoggerFactory

class UlestHendelseService(
    private val ulestHendelseRepository: UlestHendelseRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun lagreUlestHendelse(hendelse: Hendelse) {
        hendelse
            .toUlestHendelse()
            ?.also { ulestHendelse ->
                ulestHendelseRepository.upsert(ulestHendelse)
                log.info("Lagret ulest hendelse ${hendelse.id} for deltaker ${hendelse.deltaker.id}")
            } ?: { log.warn("Ikke lagret ulest hendelse ${hendelse.id} for deltaker ${hendelse.deltaker.id}") }
    }
}
