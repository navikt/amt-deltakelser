package no.nav.amt.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

/**
 * Produserer gjennomføringsdata til det interne topicet `amt.gjennomforing-intern`.
 *
 * amt-deltaker slår sammen gjennomføring (`siste-tiltaksgjennomforinger-v2`) og tiltakstype
 * (`siste-tiltakstyper-v3`) fra Mulighetsrommet og deler dem videre her, slik at
 * amt-deltaker-bff, amt-tiltaksarrangor-bff og amt-aktivitetskort-publisher på sikt kan slutte
 * å lese mulighetsrommet-topicene direkte.
 */
class AmtGjennomforingProducer(
    private val outboxService: OutboxService,
    private val producer: Producer<String, String>,
) {
    fun produce(payload: AmtGjennomforingPayload) {
        outboxService.insertRecord(
            topic = Environment.AMT_GJENNOMFORING_TOPIC,
            key = payload.id,
            value = payload,
        )
    }

    fun produceTombstone(gjennomforingId: UUID) = producer.tombstone(
        topic = Environment.AMT_GJENNOMFORING_TOPIC,
        key = gjennomforingId.toString(),
    )
}
