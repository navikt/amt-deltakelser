package no.nav.amt.deltaker.utils

import io.mockk.coVerify
import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.hendelse.Hendelse
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.outbox.OutboxService
import java.util.UUID

inline fun <reified T : Any> OutboxService.assertProduced(
    expectedDeltakerId: UUID,
    expectedTopic: String,
) {
    coVerify {
        insertRecord(
            key = expectedDeltakerId,
            value = ofType<T>(),
            topic = expectedTopic,
            suppressOutsideTxWarning = any(),
        )
    }
}

inline fun <reified T : HendelseType> OutboxService.assertProducedHendelse(expectedDeltakerId: UUID) {
    coVerify {
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
    coVerify {
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
