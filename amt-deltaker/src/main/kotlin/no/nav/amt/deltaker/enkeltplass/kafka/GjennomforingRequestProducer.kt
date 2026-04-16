package no.nav.amt.deltaker.enkeltplass.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.outbox.OutboxService

class GjennomforingRequestProducer(
    private val outboxService: OutboxService,
) {
    suspend fun produce(payload: GjennomforingRequestPayload) {
        outboxService.insertRecord(
            topic = Environment.GJENNOMFORING_REQUEST_TOPIC,
            key = payload.gjennomforingId,
            value = payload,
        )
    }
}
