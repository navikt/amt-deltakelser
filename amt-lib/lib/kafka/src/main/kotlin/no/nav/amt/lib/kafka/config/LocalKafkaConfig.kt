package no.nav.amt.lib.kafka.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringSerializer

class LocalKafkaConfig(
    private val kafkaBrokers: String = System.getenv("KAFKA_BROKERS") ?: "localhost:9092",
    private val kafkaAutoOffsetReset: String = "earliest",
) : KafkaConfig {
    override fun commonConfig() = mapOf(
        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
    )

    override fun <K, V> consumerConfig(
        keyDeserializer: Deserializer<K>,
        valueDeserializer: Deserializer<V>,
        groupId: String,
    ) = mapOf(
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to keyDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to valueDeserializer::class.java,
        ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to MAX_POLL_INTERVAL_MS,
    ) + commonConfig()

    override fun producerConfig() = mapOf(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.RETRIES_CONFIG to Int.MAX_VALUE,
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,
    ) + commonConfig()
}
