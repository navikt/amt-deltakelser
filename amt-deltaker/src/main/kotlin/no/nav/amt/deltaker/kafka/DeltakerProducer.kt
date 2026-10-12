package no.nav.amt.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.models.kafka.DeltakerKafkaPayload
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

class DeltakerProducer(
    private val outboxService: OutboxService,
) {
    fun produce(deltakerV2Dto: DeltakerKafkaPayload) {
        outboxService.insertRecord(
            topic = Environment.DELTAKER_V2_TOPIC,
            key = deltakerV2Dto.id,
            value = deltakerV2Dto,
        )
    }

    fun produceTombstone(deltakerId: UUID) = outboxService.insertTombstone(
        topic = Environment.DELTAKER_V2_TOPIC,
        key = deltakerId,
    )
}
