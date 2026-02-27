package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerEksternV1Dto
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

class DeltakerEksternV1Producer(
    private val outboxService: OutboxService,
    private val producer: Producer<String, String>,
) {
    fun produce(deltakerEksternV1Dto: DeltakerEksternV1Dto) {
        outboxService.insertRecord(
            topic = Environment.DELTAKER_EKSTERN_V1_TOPIC,
            key = deltakerEksternV1Dto.id,
            value = deltakerEksternV1Dto,
        )
    }

    fun produceTombstone(deltakerId: UUID) = producer.tombstone(
        topic = Environment.DELTAKER_EKSTERN_V1_TOPIC,
        key = deltakerId.toString(),
    )
}
