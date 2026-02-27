package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerV1Dto
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

class DeltakerV1Producer(
    private val outboxService: OutboxService,
    private val producer: Producer<String, String>,
) {
    fun produce(deltakerV1Dto: DeltakerV1Dto) {
        outboxService.insertRecord(
            topic = Environment.DELTAKER_V1_TOPIC,
            key = deltakerV1Dto.id,
            value = deltakerV1Dto,
        )
    }

    fun produceTombstone(deltakerId: UUID) = producer.tombstone(
        topic = Environment.DELTAKER_V1_TOPIC,
        key = deltakerId.toString(),
    )
}
