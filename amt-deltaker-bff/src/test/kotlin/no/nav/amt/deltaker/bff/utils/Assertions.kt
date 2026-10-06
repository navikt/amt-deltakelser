package no.nav.amt.deltaker.bff.utils

import io.mockk.verify
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorsDeltakerlistePayload
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorDeltakerlisteTilgang
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
 * Verifiserer at et tombstone-record er lagt i outbox for tiltakskoordinatorer-topicen.
 */
fun OutboxService.assertProducedTombstone(tilgang: TiltakskoordinatorDeltakerlisteTilgang) {
    verify {
        insertTombstone(
            key = tilgang.id,
            topic = Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC,
            suppressOutsideTxWarning = any(),
        )
    }
}

fun OutboxService.assertProducedTombstone(tilgang: TiltakskoordinatorsDeltakerlistePayload) {
    verify {
        insertTombstone(
            key = tilgang.id,
            topic = Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC,
            suppressOutsideTxWarning = any(),
        )
    }
}
