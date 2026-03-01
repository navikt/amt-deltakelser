package no.nav.amt.deltaker.bff.deltaker.forslag.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Melding
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class ArrangorMeldingConsumer(
    private val forslagRepository: ForslagRepository,
    private val isDev: Boolean = Environment.isDev(),
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.ARRANGOR_MELDING_TOPIC,
        consumeFunc = ::consume,
    )

    suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            log.warn("Mottok tombstone for melding med id: $key")
            forslagRepository.delete(key)
            return
        }
        val melding = objectMapper.readValue<Melding>(value)
        if (melding is Forslag) {
            if (melding.status is Forslag.Status.VenterPaSvar) {
                if (forslagRepository.kanLagres(melding.deltakerId)) {
                    forslagRepository.upsert(melding)
                    log.info("Lagret forslag med id $key")
                } else {
                    if (isDev) {
                        log.error("Mottatt forslag på deltaker som ikke finnes, deltakerid ${melding.deltakerId}, ignorerer")
                    } else {
                        throw RuntimeException("Mottatt forslag på deltaker som ikke finnes, deltakerid ${melding.deltakerId}")
                    }
                }
            } else {
                forslagRepository.delete(key)
                log.info("Slettet forslag med status ${melding.status.javaClass.simpleName}, id $key")
            }
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
