package no.nav.amt.deltaker.bff.navtiltakskoordinator.ulesthendelse

import no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions.toUlestHendelse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulesthendelse.model.UlestHendelse
import no.nav.amt.lib.models.hendelse.Hendelse
import org.slf4j.LoggerFactory
import java.util.UUID

class UlestHendelseService(
    private val ulestHendelseRepository: UlestHendelseRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun lagreUlestHendelse(hendelse: Hendelse) {
        hendelse
            .toUlestHendelse()
            ?.also { ulestHendelse ->
                ulestHendelseRepository.upsert(ulestHendelse)
                log.info("Lagret ulest hendelse ${hendelse.id} for deltaker ${hendelse.deltaker.id}")
            } ?: { log.warn("Ikke lagret ulest hendelse ${hendelse.id} for deltaker ${hendelse.deltaker.id}") }
    }

    suspend fun getUlesteHendelserForDeltaker(deltakerId: UUID): List<UlestHendelse> = ulestHendelseRepository.getForDeltaker(deltakerId)

    suspend fun delete(hendelseId: UUID) {
        ulestHendelseRepository.delete(hendelseId)
        log.info("Slettet ulest hendelse $hendelseId")
    }
}
