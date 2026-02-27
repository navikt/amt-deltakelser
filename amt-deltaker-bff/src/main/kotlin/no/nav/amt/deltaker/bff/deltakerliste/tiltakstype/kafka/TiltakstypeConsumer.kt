package no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class TiltakstypeConsumer(
    private val repository: TiltakstypeRepository,
) : Consumer<UUID, String?> {
    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.TILTAKSTYPE_TOPIC,
        consumerGroupId = Environment.KAFKA_CONSUMER_GROUP_ID + "tiltakstyper",
        consumeFunc = ::consume,
    )

    suspend fun consume(key: UUID, value: String?) {
        value?.let { handterTiltakstype(objectMapper.readValue(it)) }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    private fun handterTiltakstype(tiltakstype: TiltakstypeDto) {
        repository.upsert(tiltakstype.toModel())
    }
}
