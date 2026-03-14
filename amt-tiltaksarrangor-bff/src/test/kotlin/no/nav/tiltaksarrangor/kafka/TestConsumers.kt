package no.nav.tiltaksarrangor.kafka

import no.nav.amt.lib.kafka.ManagedKafkaConsumer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.tiltaksarrangor.IntegrationTest.Companion.kafkaContainer
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.UUID

fun stringStringConsumer(topic: String, block: suspend (k: String, v: String) -> Unit): ManagedKafkaConsumer<String, String> {
	val config = LocalKafkaConfig(kafkaContainer.bootstrapServers).consumerConfig(
		keyDeserializer = StringDeserializer(),
		valueDeserializer = StringDeserializer(),
		groupId = "test-consumer-${UUID.randomUUID()}",
	)

	return ManagedKafkaConsumer(topic, config, consume = block)
}
