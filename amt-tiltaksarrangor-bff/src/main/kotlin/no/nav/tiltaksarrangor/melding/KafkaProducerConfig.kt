package no.nav.tiltaksarrangor.melding

import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.KafkaConfig
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaProducerConfig {
    @Bean
    @Profile("default")
    fun config(): KafkaConfig = KafkaConfigImpl()

    @Bean
    @Profile("local")
    fun localConfig(): KafkaConfig = LocalKafkaConfig(System.getenv("KAFKA_BROKERS") ?: "localhost:9092")

    @Bean(destroyMethod = "close")
    fun producer(kafkaConfig: KafkaConfig) = Producer<String, String>(kafkaConfig)
}
