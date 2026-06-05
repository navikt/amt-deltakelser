package no.nav.amt.deltaker.utils

import io.mockk.verify
import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.hendelse.Hendelse
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

inline fun <reified T : Any> OutboxService.assertProduced(
    expectedKey: UUID,
    expectedTopic: String,
) {
    verify {
        insertRecord(
            key = expectedKey,
            value = ofType<T>(),
            topic = expectedTopic,
            suppressOutsideTxWarning = any(),
        )
    }
}

inline fun <reified T : Any> OutboxService.assertNotProduced(
    expectedKey: UUID,
    expectedTopic: String,
) {
    verify(exactly = 0) {
        insertRecord(
            key = expectedKey,
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

inline fun <reified T : HendelseType> OutboxService.assertNotProducedHendelse(expectedDeltakerId: UUID) {
    verify(exactly = 0) {
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
