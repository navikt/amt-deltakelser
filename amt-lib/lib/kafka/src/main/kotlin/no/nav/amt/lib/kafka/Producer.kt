package no.nav.amt.lib.kafka

import no.nav.amt.lib.kafka.config.KafkaConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class Producer<K, V>(
    kafkaConfig: KafkaConfig,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val producer = KafkaProducer<K, V>(kafkaConfig.producerConfig())

    fun close() = producer.close()

    fun produce(
        topic: String,
        key: K,
        value: V,
    ) {
        val record = ProducerRecord(
            topic,
            key,
            value,
        )

        val metadata = producer.send(record).get()

        log.info(
            "Produserte melding til topic ${metadata.topic()}, " +
                "key=$key, " +
                "offset=${metadata.offset()}, " +
                "partition=${metadata.partition()}",
        )
    }

    fun tombstone(topic: String, key: K) {
        val value: V? = null
        val record = ProducerRecord(
            topic,
            key,
            value,
        )

        val metadata = producer.send(record).get()

        log.info(
            "Produserte tombstone til topic ${metadata.topic()}, " +
                "key=$key, " +
                "offset=${metadata.offset()}, " +
                "partition=${metadata.partition()}",
        )
    }
}
