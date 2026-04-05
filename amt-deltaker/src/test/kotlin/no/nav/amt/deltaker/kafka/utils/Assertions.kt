package no.nav.amt.deltaker.kafka.utils

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.verify
import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.hendelse.Hendelse
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID
import kotlin.reflect.KClass

inline fun <reified T : Any> OutboxService.assertProduced(
    expectedDeltakerId: UUID,
    expectedTopic: String,
) {
    verify {
        insertRecord(
            key = expectedDeltakerId,
            value = ofType<T>(),
            topic = expectedTopic,
            suppressOutsideTxWarning = any(),
        )
    }
}

inline fun <reified T : HendelseType> OutboxService.assertProducedHendelse(expectedDeltakerId: UUID) {
    verify {
        insertRecord(
            key = expectedDeltakerId,
            value = match {
                it is Hendelse && it.payload is T
            },
            topic = Environment.DELTAKER_HENDELSE_TOPIC,
            suppressOutsideTxWarning = any(),
        )
    }
}

inline fun <reified T : Forslag.Status> OutboxService.assertProducedForslag(expectedForslagId: UUID) {
    verify {
        insertRecord(
            key = expectedForslagId,
            value = match {
                it is Forslag && it.status is T
            },
            topic = Environment.ARRANGOR_MELDING_TOPIC,
            suppressOutsideTxWarning = any(),
        )
    }
}

suspend fun <T : HendelseType> assertProducedHendelse(
    deltakerId: UUID,
    hendelsetype: KClass<T>,
) {
    val cache = mutableMapOf<UUID, Hendelse>()

    val consumer = stringStringConsumer(Environment.DELTAKER_HENDELSE_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    eventually {
        assertSoftly(cache[deltakerId].shouldNotBeNull()) {
            deltaker.id shouldBe deltakerId
            payload::class shouldBe hendelsetype
        }
    }

    consumer.close()
}

fun sammenlignForslagStatus(
    a: Forslag.Status,
    b: Forslag.Status,
) {
    when (a) {
        is Forslag.Status.VenterPaSvar -> {
            b as Forslag.Status.VenterPaSvar
            a shouldBe b
        }

        is Forslag.Status.Avvist -> {
            b as Forslag.Status.Avvist
            a.avvist shouldBeCloseTo b.avvist
            a.avvistAv shouldBe b.avvistAv
            a.begrunnelseFraNav shouldBe b.begrunnelseFraNav
        }

        is Forslag.Status.Godkjent -> {
            b as Forslag.Status.Godkjent
            a.godkjent shouldBeCloseTo b.godkjent
            a.godkjentAv shouldBe b.godkjentAv
        }

        is Forslag.Status.Tilbakekalt -> {
            b as Forslag.Status.Tilbakekalt
            a.tilbakekalt shouldBeCloseTo b.tilbakekalt
            a.tilbakekaltAvArrangorAnsattId shouldBe b.tilbakekaltAvArrangorAnsattId
        }

        is Forslag.Status.Erstattet -> {
            b as Forslag.Status.Erstattet
            a.erstattetMedForslagId shouldBe b.erstattetMedForslagId
            a.erstattet shouldBeCloseTo b.erstattet
        }
    }
}
