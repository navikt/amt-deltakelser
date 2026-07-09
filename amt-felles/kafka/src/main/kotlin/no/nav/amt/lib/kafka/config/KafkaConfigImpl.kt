package no.nav.amt.lib.kafka.config

import no.nav.amt.lib.utils.getEnvVar
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringSerializer

const val MAX_POLL_INTERVAL_MS = 300_000

class KafkaConfigImpl(
    private val autoOffsetReset: String = AUTO_OFFSET_RESET_EARLIEST,
) : KafkaConfig {
    private val kafkaBrokers = getEnvVar("KAFKA_BROKERS")
    private val kafkaTruststorePath = getEnvVar("KAFKA_TRUSTSTORE_PATH")
    private val kafkaCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD")
    private val kafkaKeystorePath = getEnvVar("KAFKA_KEYSTORE_PATH")

    override fun commonConfig() = mapOf(
        BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
    ) + securityConfig()

    private fun securityConfig() = mapOf(
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to KAFKA_SECURITY_PROTOCOL_SSL,
        SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "", // Disable server host name verification
        SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to KEYSTORE_TYPE_JKS,
        SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to KEYSTORE_TYPE_PKCS12,
        SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafkaTruststorePath,
        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
        SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafkaKeystorePath,
        SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
        SslConfigs.SSL_KEY_PASSWORD_CONFIG to kafkaCredstorePassword,
    )

    override fun <K, V> consumerConfig(
        keyDeserializer: Deserializer<K>,
        valueDeserializer: Deserializer<V>,
        groupId: String,
    ) = mapOf(
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetReset,
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

    companion object {
        const val AUTO_OFFSET_RESET_EARLIEST = "earliest"
        const val AUTO_OFFSET_RESET_LATEST = "latest"
        private const val KEYSTORE_TYPE_JKS = "JKS"
        private const val KEYSTORE_TYPE_PKCS12 = "PKCS12"
        private const val KAFKA_SECURITY_PROTOCOL_SSL = "SSL"
    }
}
