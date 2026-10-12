package no.nav.amt.deltaker.bff.navtiltakskoordinator

import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

// Hva brukes denne til?
class TiltakskoordinatorsDeltakerlisteProducer(
    private val outboxService: OutboxService,
) {
    fun produce(deltakerlisteDto: TiltakskoordinatorsDeltakerlistePayload) {
        outboxService.insertRecord(
            topic = Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC,
            key = deltakerlisteDto.id,
            value = deltakerlisteDto,
        )
    }

    fun produceTombstone(id: UUID) {
        outboxService.insertTombstone(
            topic = Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC,
            key = id,
        )
    }
}
