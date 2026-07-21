package no.nav.amt.aktivitetskort.kafka.consumer

import no.nav.amt.aktivitetskort.kafka.consumer.dto.TiltakstypePayload
import no.nav.amt.aktivitetskort.repositories.TiltakstypeRepository
import no.nav.amt.aktivitetskort.service.FeilmeldingService
import no.nav.amt.aktivitetskort.service.KafkaConsumerService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Component
class KafkaConsumer(
    private val kafkaConsumerService: KafkaConsumerService,
    private val feilmeldingService: FeilmeldingService,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [DELTAKER_TOPIC, ARRANGOR_TOPIC, TILTAKSTYPE_TOPIC, DELTAKERLISTE_V2_TOPIC, FEIL_TOPIC],
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun listen(
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        when (record.topic()) {
            ARRANGOR_TOPIC -> {
                kafkaConsumerService.arrangorHendelse(
                    UUID.fromString(record.key()),
                    record.value()?.let { objectMapper.readValue(it) },
                )
            }

            TILTAKSTYPE_TOPIC -> {
                objectMapper
                    .readValue<TiltakstypePayload>(record.value())
                    .let { tiltakstypeRepository.upsert(it.toModel()) }
            }

            DELTAKERLISTE_V2_TOPIC -> {
                kafkaConsumerService.deltakerlisteHendelse(
                    id = UUID.fromString(record.key()),
                    value = record.value(),
                )
            }

            DELTAKER_TOPIC -> {
                kafkaConsumerService.deltakerHendelse(
                    id = UUID.fromString(record.key()),
                    deltaker = record.value()?.let { objectMapper.readValue(it) },
                    offset = record.offset(),
                )
            }

            FEIL_TOPIC -> {
                feilmeldingService.handleFeilmelding(
                    UUID.fromString(record.key()),
                    record.value()?.let { objectMapper.readValue(it) },
                )
            }
        }

        ack.acknowledge()
    }
}
