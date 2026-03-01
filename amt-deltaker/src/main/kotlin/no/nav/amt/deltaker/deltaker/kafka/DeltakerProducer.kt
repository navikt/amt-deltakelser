package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

class DeltakerProducer(
    private val outboxService: OutboxService,
    private val producer: Producer<String, String>,
) {
    fun produce(deltakerV2Dto: DeltakerKafkaPayload) {
        outboxService.insertRecord(
            topic = Environment.DELTAKER_V2_TOPIC,
            key = deltakerV2Dto.id,
            value = deltakerV2Dto,
        )
    }

    fun produceTombstone(deltakerId: UUID) = producer.tombstone(
        topic = Environment.DELTAKER_V2_TOPIC,
        key = deltakerId.toString(),
    )
}
