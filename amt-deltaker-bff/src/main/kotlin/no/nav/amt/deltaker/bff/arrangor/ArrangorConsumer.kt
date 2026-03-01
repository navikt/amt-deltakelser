package no.nav.amt.deltaker.bff.arrangor

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class ArrangorConsumer(
    private val repository: ArrangorRepository,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.AMT_ARRANGOR_TOPIC,
        consumeFunc = ::consume,
    )

    suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            repository.delete(key)
            log.info("Slettet arrangør med id $key")
        } else {
            repository.upsert(objectMapper.readValue(value))
            log.info("Lagret arrangør med id $key")
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
