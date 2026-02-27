package no.nav.amt.distribusjon.tiltakshendelse

import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.tiltakshendelse.model.Tiltakshendelse
import no.nav.amt.distribusjon.tiltakshendelse.model.toDto
import no.nav.amt.lib.outbox.OutboxService

class TiltakshendelseProducer(
    private val outboxService: OutboxService,
) {
    fun produce(tiltakshendelse: Tiltakshendelse) {
        outboxService.insertRecord(
            key = tiltakshendelse.id,
            value = tiltakshendelse.toDto(),
            topic = Environment.TILTAKSHENDELSE_TOPIC,
        )
    }
}
