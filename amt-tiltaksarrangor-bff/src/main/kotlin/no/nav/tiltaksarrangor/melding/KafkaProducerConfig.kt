package no.nav.tiltaksarrangor.melding

import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.KafkaConfig
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
class KafkaProducerConfig {
	@Bean
	@Profile("default")
	fun config(): KafkaConfig = KafkaConfigImpl()

	@Bean(destroyMethod = "close")
	fun producer(kafkaConfig: KafkaConfig) = Producer<String, String>(kafkaConfig)
}
