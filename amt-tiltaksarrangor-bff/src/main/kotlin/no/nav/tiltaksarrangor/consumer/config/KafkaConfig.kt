package no.nav.tiltaksarrangor.consumer.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

@Configuration(proxyBeanMethods = false)
class KafkaConfig(
	@Value($$"${KAFKA_BROKERS}") private val kafkaBrokers: String,
	@Value($$"${KAFKA_SECURITY_PROTOCOL:SSL}") private val kafkaSecurityProtocol: String,
	@Value($$"${KAFKA_TRUSTSTORE_PATH}") private val kafkaTruststorePath: String,
	@Value($$"${KAFKA_CREDSTORE_PASSWORD}") private val kafkaCredstorePassword: String,
	@Value($$"${KAFKA_KEYSTORE_PATH}") private val kafkaKeystorePath: String,
	@Value($$"${kafka.auto-offset-reset}") private val kafkaAutoOffsetReset: String,
) {
	private val securityConfig = mapOf(
		CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
		// Disable server host name verification
		SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
		SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to JAVA_KEY_STORE,
		SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to PKCS12,
		SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafkaTruststorePath,
		SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
		SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafkaKeystorePath,
		SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
		SslConfigs.SSL_KEY_PASSWORD_CONFIG to kafkaCredstorePassword,
	)

	val commonKafkaConfig = mapOf(
		BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
	) + securityConfig

	@Bean
	fun kafkaListenerContainerFactory(kafkaErrorHandler: KafkaErrorHandler): ConcurrentKafkaListenerContainerFactory<String, String> {
		val config =
			mapOf(
				ConsumerConfig.GROUP_ID_CONFIG to "amt-tiltaksarrangor-bff-consumer",
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
				ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
			) + commonKafkaConfig

		val consumerFactory = DefaultKafkaConsumerFactory<String, String>(config)

		return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
			setConsumerFactory(consumerFactory)
			setCommonErrorHandler(kafkaErrorHandler)
			containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
		}
	}

	companion object {
		private const val JAVA_KEY_STORE = "JKS"
		private const val PKCS12 = "PKCS12"
	}
}
