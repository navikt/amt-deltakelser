package no.nav.amt.deltaker.bff.deltaker.forslag.kafka

import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.lib.models.arrangor.melding.Melding
import no.nav.amt.lib.outbox.OutboxService

class ArrangorMeldingProducer(
    private val outboxService: OutboxService,
) {
    fun produce(melding: Melding) {
        outboxService.insertRecord(
            topic = Environment.ARRANGOR_MELDING_TOPIC,
            key = melding.id,
            value = melding,
        )
    }
}
