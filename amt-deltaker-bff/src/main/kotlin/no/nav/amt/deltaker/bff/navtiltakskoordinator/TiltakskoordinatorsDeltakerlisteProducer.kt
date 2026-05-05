package no.nav.amt.deltaker.bff.navtiltakskoordinator

import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

// Hva brukes denne til?
class TiltakskoordinatorsDeltakerlisteProducer(
    private val outboxService: OutboxService,
    private val producer: Producer<String, String>,
) {
    fun produce(deltakerlisteDto: TiltakskoordinatorsDeltakerlistePayload) {
        outboxService.insertRecord(
            topic = Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC,
            key = deltakerlisteDto.id,
            value = deltakerlisteDto,
        )
    }

    fun produceTombstone(id: UUID) {
        producer.tombstone(
            topic = Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC,
            key = id.toString(),
        )
    }
}
