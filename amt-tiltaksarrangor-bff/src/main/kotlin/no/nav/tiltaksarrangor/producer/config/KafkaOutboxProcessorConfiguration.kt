package no.nav.tiltaksarrangor.producer.config

import net.javacrumbs.shedlock.core.LockProvider
import no.nav.common.job.leader_election.LeaderElectionClient
import no.nav.common.job.leader_election.ShedLockLeaderElectionClient
import no.nav.common.kafka.producer.KafkaProducerClient
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import no.nav.common.kafka.producer.feilhandtering.publisher.BatchedKafkaProducerRecordPublisher
import no.nav.common.kafka.producer.feilhandtering.util.KafkaProducerRecordProcessorBuilder
import no.nav.common.kafka.spring.PostgresJdbcTemplateProducerRepository
import no.nav.tiltaksarrangor.melding.MELDING_TOPIC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaOutboxProcessorConfiguration {
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
        .withTopicWhitelist(listOf(MELDING_TOPIC))
        .withRecordsBatchSize(100)
        .withShutdownHookEnabled(false)
        .build()
}
