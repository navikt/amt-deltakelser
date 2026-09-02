package no.nav.amt.deltaker.bff.tiltak

import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskoder.skalKometLagreTiltakstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import no.nav.amt.lib.utils.objectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class TiltakConsumer(
    private val repository: TiltakRepository,
) : Consumer<UUID, String?> {
    private val consumer = KafkaConsumerFactory.buildManagedKafkaConsumer(
        topic = Environment.TILTAKSTYPE_TOPIC,
        consumerGroupId = Environment.KAFKA_CONSUMER_GROUP_ID + "tiltakstyper",
        consumeFunc = ::consume,
    )

    suspend fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null || !skalKometLagreTiltakstype(value, objectMapper)) {
            return
        }

        val tiltakstypeDto = objectMapper.readValue<TiltakstypeDto>(value)
        repository.upsert(tiltakstypeDto.toModel())
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
