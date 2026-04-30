package no.nav.amt.deltaker.tiltaksarrangor

import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.outbox.OutboxService

class ArrangorMeldingProducer(
    private val outboxService: OutboxService,
) {
    fun produce(forslag: Forslag) {
        outboxService.insertRecord(
            topic = Environment.ARRANGOR_MELDING_TOPIC,
            key = forslag.id,
            value = forslag,
        )
    }
}
