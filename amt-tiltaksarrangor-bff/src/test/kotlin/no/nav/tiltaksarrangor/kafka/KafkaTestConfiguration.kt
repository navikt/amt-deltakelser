package no.nav.tiltaksarrangor.kafka

import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.tiltaksarrangor.IntegrationTest.Companion.kafkaContainer
import no.nav.tiltaksarrangor.consumer.config.KafkaConfig
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaConsumerFactory

@TestConfiguration(proxyBeanMethods = false)
class KafkaTestConfiguration(
	private val kafkaConfig: KafkaConfig,
) {
	@Bean
	fun testKafkaConsumer(): Consumer<String, String> = DefaultKafkaConsumerFactory(
		mapOf(
			ConsumerConfig.GROUP_ID_CONFIG to "bff-consumer",
			ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
			ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
			ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
		).plus(kafkaConfig.commonKafkaConfig),
		StringDeserializer(),
		StringDeserializer(),
	).createConsumer()

	@Bean
	fun testKafkaProducer(): KafkaProducer<String, String> = KafkaProducer(
		mapOf(
			ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
			ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
			ProducerConfig.ACKS_CONFIG to "all",
			ProducerConfig.RETRIES_CONFIG to 10,
			ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 100,
		).plus(kafkaConfig.commonKafkaConfig),
	)

	@Bean
	fun localKafkaConfig(): no.nav.amt.lib.kafka.config.KafkaConfig = LocalKafkaConfig(kafkaContainer.bootstrapServers)
}
