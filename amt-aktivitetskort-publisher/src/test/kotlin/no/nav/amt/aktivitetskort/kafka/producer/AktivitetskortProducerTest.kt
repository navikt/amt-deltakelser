package no.nav.amt.aktivitetskort.kafka.producer

import io.mockk.mockk
import io.mockk.verify
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@JsonTest
class AktivitetskortProducerTest(
    @Autowired private val objectMapper: ObjectMapper,
) {
    private val producerRecordStorage = mockk<KafkaProducerRecordStorage>(
        relaxed = true,
    )

    private val producer = AktivitetskortProducer(
        producerRecordStorage = producerRecordStorage,
        objectMapper = objectMapper,
    )

    @Nested
    inner class SlettAktivitetskort {
        @Test
        fun `lagrer kassering i outbox`() {
            // Arrange
            val aktivitetskortId = UUID.randomUUID()
            val personIdent = "12345678901"
            val navIdent = "Z123456"

            // Act
            producer.slettAktivitetskort(
                aktivitetskortId = aktivitetskortId,
                personIdent = personIdent,
                navIdent = navIdent,
            )

            // Assert
            verify(exactly = 1) { producerRecordStorage.store(any()) }
        }
    }
}
