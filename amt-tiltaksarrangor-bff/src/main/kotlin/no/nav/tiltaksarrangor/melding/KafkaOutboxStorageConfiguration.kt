package no.nav.tiltaksarrangor.melding

import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordStorage
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

@Configuration(proxyBeanMethods = false)
class KafkaOutboxStorageConfiguration {
    @Bean
    fun kafkaProducerRepository(jdbcTemplate: JdbcTemplate) = PostgresJdbcTemplateProducerRepository(jdbcTemplate)

    @Bean
    fun kafkaProducerRecordStorage(producerRepository: PostgresJdbcTemplateProducerRepository) =
        KafkaProducerRecordStorage(producerRepository)
}
