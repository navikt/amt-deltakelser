package no.nav.amt.deltaker.bff.utils

import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.lib.kafka.ManagedKafkaConsumer
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import java.util.UUID

object KafkaConsumerFactory {
    const val AUTO_OFFSET_RESET_EARLIEST = "earliest"
    const val AUTO_OFFSET_RESET_LATEST = "latest"

    /**
     * Oppretter instans av [ManagedKafkaConsumer] med [UUID] som nøkkel og
     * nullable [String] som verdi.
     *
     * @param topic Navnet på Kafka-topic som det skal lyttes på.
     * @param consumerGroupId ID-en til consumer-gruppen. Default: [Environment.KAFKA_CONSUMER_GROUP_ID].
     * @param consumeFunc Funksjon som håndterer mottatte meldinger.
     *
     * @return Instans av [ManagedKafkaConsumer] konfigurert for spesifisert topic og consumer group.
     */
    fun buildManagedKafkaConsumer(
        topic: String,
        consumerGroupId: String = Environment.KAFKA_CONSUMER_GROUP_ID,
        kafkaAutoOffsetReset: String = AUTO_OFFSET_RESET_EARLIEST, // alt: AUTO_OFFSET_RESET_LATEST
        consumeFunc: suspend (key: UUID, value: String?) -> Unit,
    ): ManagedKafkaConsumer<UUID, String?> {
        val kafkaConfig = if (Environment.isLocal()) {
            LocalKafkaConfig(kafkaAutoOffsetReset = kafkaAutoOffsetReset)
        } else {
            KafkaConfigImpl(autoOffsetReset = kafkaAutoOffsetReset)
        }

        return ManagedKafkaConsumer(
            topic = topic,
            config = kafkaConfig.consumerConfig(
                keyDeserializer = UUIDDeserializer(),
                valueDeserializer = StringDeserializer(),
                groupId = consumerGroupId,
            ),
            consume = consumeFunc,
        )
    }
}
