package no.nav.tiltaksarrangor.consumer

import no.nav.tiltaksarrangor.melding.MELDING_TOPIC
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
    private val deltakerlisteConsumerService: DeltakerlisteConsumerService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [
            ARRANGOR_TOPIC,
            ARRANGOR_ANSATT_TOPIC,
            GJENNOMFORING_INTERN_TOPIC,
            DELTAKER_TOPIC,
            ENDRINGSMELDING_TOPIC,
            NAV_ANSATT_TOPIC,
            MELDING_TOPIC,
            NAV_ENHET_TOPIC,
        ],
        properties = ["auto.offset.reset = earliest"],
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun listen(
        consumerRecord: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        when (consumerRecord.topic()) {
            ARRANGOR_TOPIC -> kafkaConsumerService.lagreArrangor(
                UUID.fromString(consumerRecord.key()),
                consumerRecord.value()?.let { objectMapper.readValue(it) },
            )

            ARRANGOR_ANSATT_TOPIC -> kafkaConsumerService.lagreAnsatt(
                UUID.fromString(consumerRecord.key()),
                consumerRecord.value()?.let { objectMapper.readValue(it) },
            )

            GJENNOMFORING_INTERN_TOPIC -> deltakerlisteConsumerService.handleGjennomforing(
                deltakerlisteId = UUID.fromString(consumerRecord.key()),
                value = consumerRecord.value(),
            )

            DELTAKER_TOPIC -> kafkaConsumerService.lagreDeltaker(
                UUID.fromString(consumerRecord.key()),
                consumerRecord.value(),
            )

            ENDRINGSMELDING_TOPIC -> kafkaConsumerService.lagreEndringsmelding(
                UUID.fromString(consumerRecord.key()),
                consumerRecord.value()?.let { objectMapper.readValue(it) },
            )

            NAV_ANSATT_TOPIC -> kafkaConsumerService.lagreNavAnsatt(
                objectMapper.readValue(consumerRecord.value()),
            )

            MELDING_TOPIC -> kafkaConsumerService.handleMelding(
                UUID.fromString(consumerRecord.key()),
                consumerRecord.value()?.let { objectMapper.readValue(it) },
            )

            NAV_ENHET_TOPIC -> kafkaConsumerService.lagreNavEnhet(
                UUID.fromString(consumerRecord.key()),
                objectMapper.readValue(consumerRecord.value()),
            )

            else -> throw IllegalStateException("Mottok melding på ukjent topic: ${consumerRecord.topic()}")
        }

        acknowledgment.acknowledge()
    }

    companion object {
        const val ARRANGOR_TOPIC = "amt.arrangor-v1"
        const val ARRANGOR_ANSATT_TOPIC = "amt.arrangor-ansatt-v1"
        const val GJENNOMFORING_INTERN_TOPIC = "amt.gjennomforing-intern"
        const val DELTAKER_TOPIC = "amt.deltaker-v2"
        const val ENDRINGSMELDING_TOPIC = "amt.endringsmelding-v1"
        const val NAV_ANSATT_TOPIC = "amt.nav-ansatt-personalia-v1"
        const val NAV_ENHET_TOPIC = "amt.nav-enhet-v1"
    }
}
