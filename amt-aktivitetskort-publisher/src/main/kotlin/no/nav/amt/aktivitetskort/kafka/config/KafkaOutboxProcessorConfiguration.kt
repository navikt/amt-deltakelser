package no.nav.amt.aktivitetskort.kafka.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import no.nav.amt.aktivitetskort.kafka.consumer.AKTIVITETSKORT_TOPIC
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.job.leader_election.ShedLockLeaderElectionClient
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import no.nav.common.kafka.producer.feilhandtering.publisher.BatchedKafkaProducerRecordPublisher
import no.nav.common.kafka.producer.feilhandtering.util.KafkaProducerRecordProcessorBuilder
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaOutboxProcessorConfiguration {
    @Bean
    fun lockProvider(jdbcTemplate: JdbcTemplate): LockProvider {
        val configuration = JdbcTemplateLockProvider.Configuration
            .builder()
            .withJdbcTemplate(jdbcTemplate)
            .usingDbTime()
            .build()
        return JdbcTemplateLockProvider(configuration)
    }

    @Bean
    fun kafkaProducerLeaderElectionClient(lockProvider: LockProvider): LeaderElectionClient = ShedLockLeaderElectionClient(lockProvider)

    @Bean
    fun kafkaProducerRecordProcessor(
        producerRepository: PostgresJdbcTemplateProducerRepository,
        @Qualifier("kafkaOutboxProducer")
        kafkaOutboxProducer: KafkaProducerClient<ByteArray, ByteArray>,
        kafkaProducerLeaderElectionClient: LeaderElectionClient,
    ): KafkaProducerRecordProcessor = KafkaProducerRecordProcessorBuilder
        .builder()
        .withProducerRepository(producerRepository)
        .withRecordPublisher(BatchedKafkaProducerRecordPublisher(kafkaOutboxProducer))
        .withLeaderElectionClient(kafkaProducerLeaderElectionClient)
        .withTopicWhitelist(listOf(AKTIVITETSKORT_TOPIC))
        .withRecordsBatchSize(100)
        .withShutdownHookEnabled(false)
        .build()
}
