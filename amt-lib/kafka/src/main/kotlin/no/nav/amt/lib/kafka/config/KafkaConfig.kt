package no.nav.amt.lib.kafka.config

import org.apache.kafka.common.serialization.Deserializer

interface KafkaConfig {
    fun commonConfig(): Map<String, *>

    fun <K, V> consumerConfig(
        keyDeserializer: Deserializer<K>,
        valueDeserializer: Deserializer<V>,
        groupId: String,
    ): Map<String, *>

    fun producerConfig(): Map<String, *>
}
