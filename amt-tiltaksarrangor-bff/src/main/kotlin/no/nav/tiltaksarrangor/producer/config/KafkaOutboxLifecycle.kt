package no.nav.tiltaksarrangor.producer.config

import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaOutboxLifecycle(
    private val producerRecordProcessor: KafkaProducerRecordProcessor,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)

    private var running = false

    override fun start() {
        if (running) return

        log.info("Starting Kafka outbox processor...")
        producerRecordProcessor.start()
        running = true
    }

    override fun stop() {
        if (!running) return

        log.info("Stopping Kafka outbox processor...")
        producerRecordProcessor.close()
        running = false
    }

    override fun isRunning() = running
}
