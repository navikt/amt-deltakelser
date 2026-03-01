package no.nav.amt.lib.kafka

import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.testing.SingletonKafkaProvider
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.IntegerDeserializer
import org.apache.kafka.common.serialization.IntegerSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.UUIDSerializer
import java.util.Properties
import java.util.UUID

object KafkaTestUtils {
    const val TOPIC_IN_TEST = "test.topic"

    val topicPartition1 = TopicPartition("topic", 1)
    val topicPartition2 = TopicPartition("topic", 2)

    val stringConsumerConfig = LocalKafkaConfig(SingletonKafkaProvider.getHost()).consumerConfig(
        keyDeserializer = StringDeserializer(),
        valueDeserializer = StringDeserializer(),
        groupId = "test-consumer-${UUID.randomUUID()}",
    )

    val intConsumerConfig = LocalKafkaConfig(SingletonKafkaProvider.getHost()).consumerConfig(
        keyDeserializer = IntegerDeserializer(),
        valueDeserializer = IntegerDeserializer(),
        groupId = "test-consumer-${UUID.randomUUID()}",
    )

    fun produceIntInt(record: ProducerRecord<Int, Int>): RecordMetadata {
        KafkaProducer<Int, Int>(
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SingletonKafkaProvider.getHost())
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer::class.java)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, IntegerSerializer::class.java)
            },
        ).use { producer ->
            return producer.send(record).get()
        }
    }

    fun produceStringString(record: ProducerRecord<String, String>): RecordMetadata {
        KafkaProducer<String, String>(
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SingletonKafkaProvider.getHost())
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            },
        ).use { producer ->
            return producer.send(record).get()
        }
    }

    fun produceUUIDByteArray(record: ProducerRecord<UUID, ByteArray>): RecordMetadata {
        KafkaProducer<UUID, ByteArray>(
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SingletonKafkaProvider.getHost())
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, UUIDSerializer::class.java)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
            },
        ).use { producer ->
            return producer.send(record).get()
        }
    }
}
