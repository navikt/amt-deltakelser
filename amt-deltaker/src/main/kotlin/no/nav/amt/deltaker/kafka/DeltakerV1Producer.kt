package no.nav.amt.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.kafka.payload.DeltakerV1Dto
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

/*
    Producer for data til deltaker-v1 topic som arena er eneste konsument av.
    Arena leser data fra denne topicen og skriver til TILTAKDELTAKER tabell.
    Deltakelser som ikke skal skrives til arena, skal ikke produseres til topicen.
 */
class DeltakerV1Producer(
    private val outboxService: OutboxService,
) {
    fun produce(deltakerV1Dto: DeltakerV1Dto) {
        outboxService.insertRecord(
            topic = Environment.DELTAKER_V1_TOPIC,
            key = deltakerV1Dto.id,
            value = deltakerV1Dto,
        )
    }

    fun produceTombstone(deltakerId: UUID) = outboxService.insertTombstone(
        topic = Environment.DELTAKER_V1_TOPIC,
        key = deltakerId,
    )
}
