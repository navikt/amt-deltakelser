package no.nav.tiltaksarrangor.melding

import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import no.nav.common.kafka.producer.util.ProducerUtils
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

const val MELDING_TOPIC = "amt.arrangor-melding-v1"

@Service
@Transactional(propagation = Propagation.MANDATORY)
class MeldingProducer(
    private val producerRecordStorage: KafkaProducerRecordStorage,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun produce(forslag: Forslag) {
        when (forslag.status) {
            is Forslag.Status.Avvist,
            is Forslag.Status.Godkjent,
            -> error("Forsøkte å produsere forslag ${forslag.id} med status ${forslag.status::class.simpleName}")

            is Forslag.Status.Tilbakekalt,
            is Forslag.Status.Erstattet,
            is Forslag.Status.VenterPaSvar,
            -> {
                produce(forslag.id.toString(), objectMapper.writeValueAsString(forslag))
                log.info("La forslag ${forslag.id} i Kafka-outbox med status ${forslag.status::class.simpleName}")
            }
        }
    }

    fun produce(endring: EndringFraArrangor) {
        produce(endring.id.toString(), objectMapper.writeValueAsString(endring))
        log.info("La endring fra arrangør ${endring.id} i Kafka-outbox")
    }

    fun produce(vurdering: Vurdering) {
        produce(vurdering.id.toString(), objectMapper.writeValueAsString(vurdering))
        log.info("La vurdering fra arrangør ${vurdering.id} i Kafka-outbox")
    }

    private fun produce(
        key: String,
        value: String,
    ) {
        val record = ProducerRecord(MELDING_TOPIC, key, value)
        producerRecordStorage.store(ProducerUtils.serializeStringRecord(record))
    }
}
