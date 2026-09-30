package no.nav.amt.aktivitetskort.kafka.producer

import no.nav.amt.aktivitetskort.domain.Aktivitetskort
import no.nav.amt.aktivitetskort.kafka.consumer.AKTIVITETSKORT_TOPIC
import no.nav.amt.aktivitetskort.kafka.producer.dto.AktivitetskortKasseringPayload
import no.nav.amt.aktivitetskort.kafka.producer.dto.AktivitetskortPayload
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import no.nav.common.kafka.producer.util.ProducerUtils
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Legger aktivitetskort-meldinger i Kafka-outboxen (kafka_producer_record).
 *
 * MANDATORY: Alle metoder må kalles i en aktiv transaksjon, slik at outbox-raden lagres
 * atomisk sammen med dataene den beskriver. Kaster IllegalTransactionStateException ellers.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class AktivitetskortProducer(
    private val producerRecordStorage: KafkaProducerRecordStorage,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun send(aktivitetskort: Aktivitetskort) {
        val messageId = UUID.randomUUID()
        val payload = AktivitetskortPayload(
            messageId = messageId,
            aktivitetskortType = aktivitetskort.tiltakstype,
            aktivitetskort = aktivitetskort.toAktivitetskortDto(),
        )

        produce(
            key = aktivitetskort.id.toString(),
            value = objectMapper.writeValueAsString(payload),
        )
        log.info("La aktivitetskort i Kafka-outbox: ${aktivitetskort.id} messageId: $messageId")
    }

    fun slettAktivitetskort(
        aktivitetskortId: UUID,
        personIdent: String,
        navIdent: String,
    ) {
        val payload = AktivitetskortKasseringPayload(
            messageId = UUID.randomUUID(),
            aktivitetsId = aktivitetskortId,
            personIdent = personIdent,
            navIdent = navIdent,
            begrunnelse = "Kassering av duplikat aktivitetskort",
        )

        produce(
            key = aktivitetskortId.toString(),
            value = objectMapper.writeValueAsString(payload),
        )

        log.info("La kassering av aktivitetskort i Kafka-outbox: $aktivitetskortId")
    }

    private fun produce(
        key: String,
        value: String,
    ) {
        val record = ProducerRecord(
            AKTIVITETSKORT_TOPIC,
            key,
            value,
        )
        producerRecordStorage.store(ProducerUtils.serializeStringRecord(record))
    }
}
