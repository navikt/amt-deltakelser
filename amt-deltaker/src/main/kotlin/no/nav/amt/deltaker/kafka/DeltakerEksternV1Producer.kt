package no.nav.amt.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.kafka.payload.DeltakerEksternV1Dto
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

class DeltakerEksternV1Producer(
    private val outboxService: OutboxService,
) {
    fun produce(deltakerEksternV1Dto: DeltakerEksternV1Dto) {
        outboxService.insertRecord(
            topic = Environment.DELTAKER_EKSTERN_V1_TOPIC,
            key = deltakerEksternV1Dto.id,
            value = deltakerEksternV1Dto,
        )
    }

    fun produceTombstone(deltakerId: UUID) = outboxService.insertTombstone(
        topic = Environment.DELTAKER_EKSTERN_V1_TOPIC,
        key = deltakerId,
    )
}
