package no.nav.amt.distribusjon.utils

import no.nav.amt.lib.testing.SingletonKafkaProvider
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

fun produceStringString(record: ProducerRecord<String, String>): RecordMetadata = KafkaProducer<String, String>(
    Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SingletonKafkaProvider.getHost())
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    },
).use { producer ->
    producer.send(record).get()
}
