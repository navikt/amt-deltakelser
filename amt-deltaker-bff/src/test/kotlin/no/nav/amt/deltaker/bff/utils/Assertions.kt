package no.nav.amt.deltaker.bff.utils

import io.mockk.verify
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorsDeltakerlistePayload
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorDeltakerlisteTilgang
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.outbox.OutboxService

fun OutboxService.assertProduced(tilgang: TiltakskoordinatorsDeltakerlistePayload) {
    verify {
        insertRecord(
            key = tilgang.id,
            value = match { value ->
                value is TiltakskoordinatorsDeltakerlistePayload &&
                    value.id == tilgang.id &&
                    value.gjennomforingId == tilgang.gjennomforingId &&
                    value.navIdent == tilgang.navIdent
            },
            topic = Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC,
            suppressOutsideTxWarning = any(),
        )
    }
}

/**
 * Verifiserer at [Producer.tombstone] er kalt for gitt id på tiltakskoordinatorer-topicen.
 */
fun Producer<String, String>.assertProducedTombstone(tilgang: TiltakskoordinatorDeltakerlisteTilgang) {
    verify {
        tombstone(
            topic = Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC,
            key = tilgang.id.toString(),
        )
    }
}

fun Producer<String, String>.assertProducedTombstone(tilgang: TiltakskoordinatorsDeltakerlistePayload) {
    verify {
        tombstone(
            topic = Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC,
            key = tilgang.id.toString(),
        )
    }
}
