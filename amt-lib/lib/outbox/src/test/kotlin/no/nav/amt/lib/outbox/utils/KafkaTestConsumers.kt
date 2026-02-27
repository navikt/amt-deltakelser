package no.nav.amt.lib.outbox.utils

import kotlinx.coroutines.runBlocking
import no.nav.amt.lib.kafka.ManagedKafkaConsumer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.testing.SingletonKafkaProvider
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.UUID

fun stringStringConsumer(topic: String, block: suspend (k: String, v: String) -> Unit): ManagedKafkaConsumer<String, String> {
    val config = LocalKafkaConfig(SingletonKafkaProvider.getHost(), "earliest").consumerConfig(
        keyDeserializer = StringDeserializer(),
        valueDeserializer = StringDeserializer(),
        groupId = "test-consumer-${UUID.randomUUID()}",
    )

    return ManagedKafkaConsumer(
        topic = topic,
        config = config,
        consume = block,
    )
}

fun assertProduced(topic: String, block: suspend (cache: Map<String, String>) -> Unit) {
    val cache = mutableMapOf<String, String>()

    val consumer = stringStringConsumer(topic) { k, v ->
        cache[k] = v
    }

    runBlocking {
        consumer.start()
        block(cache)
        consumer.close()
    }
}
