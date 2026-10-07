package no.nav.tiltaksarrangor.producer.config

import io.micrometer.core.instrument.MeterRegistry
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.util.KafkaProducerClientBuilder
import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.common.kafka.util.KafkaPropertiesPreset
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaOutboxProducerConfig {
    @Bean("kafkaOutboxProducer")
    @Profile("default")
    fun kafkaOutboxProducer(meterRegistry: MeterRegistry): KafkaProducerClient<ByteArray, ByteArray> {
        val properties = KafkaPropertiesPreset.aivenDefaultProducerProperties(PRODUCER_ID).apply {
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
        }

        return KafkaProducerClientBuilder
            .builder<ByteArray, ByteArray>()
            .withProperties(properties)
            .withMetrics(meterRegistry)
            .build()
    }

    @Bean("kafkaOutboxProducer")
    @Profile("local")
    fun localKafkaOutboxProducer(
        @Value($$"${KAFKA_BROKERS:localhost:9092}") kafkaBrokers: String,
        meterRegistry: MeterRegistry,
    ): KafkaProducerClient<ByteArray, ByteArray> {
        val properties = KafkaPropertiesBuilder
            .producerBuilder()
            .withBrokerUrl(kafkaBrokers)
            .withBaseProperties()
            .withProducerId(PRODUCER_ID)
            .withSerializers(ByteArraySerializer::class.java, ByteArraySerializer::class.java)
            .build()

        return KafkaProducerClientBuilder
            .builder<ByteArray, ByteArray>()
            .withProperties(properties)
            .withMetrics(meterRegistry)
            .build()
    }

    companion object {
        private const val PRODUCER_ID = "amt-tiltaksarrangor-bff-outbox"
    }
}
