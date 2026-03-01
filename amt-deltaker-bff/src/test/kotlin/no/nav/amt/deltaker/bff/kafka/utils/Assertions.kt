package no.nav.amt.deltaker.bff.kafka.utils

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorDeltakerlisteTilgang
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorsDeltakerlisteDto
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

fun assertProduced(forslag: Forslag) = runTest {
    val receivedMessages = mutableMapOf<UUID, Forslag>()

    val consumer = stringStringConsumer(Environment.ARRANGOR_MELDING_TOPIC) { k, v ->
        receivedMessages[UUID.fromString(k)] = objectMapper.readValue(v!!)
    }

    consumer.start()

    eventually {
        val receivedForslag = receivedMessages[forslag.id].shouldNotBeNull()

        assertSoftly(receivedForslag) {
            id shouldBe forslag.id
            deltakerId shouldBe forslag.deltakerId
            endring shouldBe forslag.endring
            begrunnelse shouldBe forslag.begrunnelse
            opprettet shouldBe forslag.opprettet
            opprettetAvArrangorAnsattId shouldBe forslag.opprettetAvArrangorAnsattId
        }

        sammenlignForslagStatus(receivedForslag.status, forslag.status)
    }

    consumer.close()
}

fun assertProduced(tilgang: TiltakskoordinatorsDeltakerlisteDto, tombstoneExpected: Boolean = false) = runTest {
    val receivedDeltakerlister = mutableMapOf<UUID, TiltakskoordinatorsDeltakerlisteDto?>()

    val consumer = stringStringConsumer(Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC) { k, v ->
        receivedDeltakerlister[UUID.fromString(k)] = v?.let { objectMapper.readValue(it) }
    }

    consumer.start()

    eventually {
        receivedDeltakerlister.keys.contains(tilgang.id) shouldBe true

        if (tombstoneExpected) {
            receivedDeltakerlister[tilgang.id] shouldBe null
        } else {
            assertSoftly(receivedDeltakerlister[tilgang.id].shouldNotBeNull()) {
                id shouldBe tilgang.id
                gjennomforingId shouldBe tilgang.gjennomforingId
                navIdent shouldBe tilgang.navIdent
            }
        }
    }

    consumer.close()
}

fun assertProducedTombstone(tilgang: TiltakskoordinatorDeltakerlisteTilgang) = runTest {
    val receivedDeltakerlister = mutableMapOf<UUID, TiltakskoordinatorsDeltakerlisteDto?>()

    val consumer = stringStringConsumer(Environment.AMT_TILTAKSKOORDINATORS_DELTAKERLISTE_TOPIC) { k, v ->
        receivedDeltakerlister[UUID.fromString(k)] = v?.let { objectMapper.readValue(it) }
    }

    consumer.start()

    eventually {
        receivedDeltakerlister.keys.contains(tilgang.id) shouldBe true
        receivedDeltakerlister[tilgang.id] shouldBe null
    }

    consumer.close()
}

fun sammenlignForslagStatus(first: Forslag.Status, second: Forslag.Status) {
    when (first) {
        is Forslag.Status.VenterPaSvar -> {
            second as Forslag.Status.VenterPaSvar
            first shouldBe second
        }

        is Forslag.Status.Avvist -> {
            second as Forslag.Status.Avvist
            first.avvist shouldBeCloseTo second.avvist
            first.avvistAv shouldBe second.avvistAv
            first.begrunnelseFraNav shouldBe second.begrunnelseFraNav
        }

        is Forslag.Status.Godkjent -> {
            second as Forslag.Status.Godkjent
            first.godkjent shouldBeCloseTo second.godkjent
            first.godkjentAv shouldBe second.godkjentAv
        }

        is Forslag.Status.Tilbakekalt -> {
            second as Forslag.Status.Tilbakekalt
            first.tilbakekalt shouldBeCloseTo second.tilbakekalt
            first.tilbakekaltAvArrangorAnsattId shouldBe second.tilbakekaltAvArrangorAnsattId
        }

        is Forslag.Status.Erstattet -> {
            second as Forslag.Status.Erstattet
            first.erstattetMedForslagId shouldBe second.erstattetMedForslagId
            first.erstattet shouldBeCloseTo second.erstattet
        }
    }
}
