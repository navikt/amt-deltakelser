package no.nav.amt.deltaker.bff.utils

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.verify
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorsDeltakerlistePayload
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorDeltakerlisteTilgang
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.testing.shouldBeCloseTo

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

fun sammenlignForslagStatus(
    first: Forslag.Status,
    second: Forslag.Status,
) {
    when (first) {
        is Forslag.Status.VenterPaSvar -> {
            second as Forslag.Status.VenterPaSvar
            first shouldBe second
        }

        is Forslag.Status.Avvist -> {
            second as Forslag.Status.Avvist
            assertSoftly(first) {
                avvist shouldBeCloseTo second.avvist
                avvistAv shouldBe second.avvistAv
                begrunnelseFraNav shouldBe second.begrunnelseFraNav
            }
        }

        is Forslag.Status.Godkjent -> {
            second as Forslag.Status.Godkjent
            assertSoftly(first) {
                godkjent shouldBeCloseTo second.godkjent
                godkjentAv shouldBe second.godkjentAv
            }
        }

        is Forslag.Status.Tilbakekalt -> {
            second as Forslag.Status.Tilbakekalt
            assertSoftly(first) {
                tilbakekalt shouldBeCloseTo second.tilbakekalt
                tilbakekaltAvArrangorAnsattId shouldBe second.tilbakekaltAvArrangorAnsattId
            }
        }

        is Forslag.Status.Erstattet -> {
            second as Forslag.Status.Erstattet
            assertSoftly(first) {
                erstattetMedForslagId shouldBe second.erstattetMedForslagId
                erstattet shouldBeCloseTo second.erstattet
            }
        }
    }
}
