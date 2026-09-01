package no.nav.amt.deltaker.tiltak

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskoder.skalKometLagreTiltakstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import no.nav.amt.lib.utils.objectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class TiltakConsumer(
    private val repository: TiltakRepository,
) : Consumer<UUID, String?> {
    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.TILTAKSTYPE_TOPIC,
        consumeFunc = ::consume,
    )

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null || !skalKometLagreTiltakstype(value, objectMapper)) {
            return
        }

        val tiltakstypeDto = objectMapper.readValue<TiltakstypeDto>(value)
        repository.upsert(tiltakstypeDto.toModel())
    }
}
