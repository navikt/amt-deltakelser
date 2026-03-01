package no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class TiltakstypeConsumer(
    private val repository: TiltakstypeRepository,
) : Consumer<UUID, String?> {
    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.TILTAKSTYPE_TOPIC,
        consumeFunc = ::consume,
    )

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    suspend fun consume(key: UUID, value: String?) {
        value?.let { handterTiltakstype(objectMapper.readValue(it)) }
    }

    private fun handterTiltakstype(tiltakstype: TiltakstypeDto) = repository.upsert(tiltakstype.toModel())
}
