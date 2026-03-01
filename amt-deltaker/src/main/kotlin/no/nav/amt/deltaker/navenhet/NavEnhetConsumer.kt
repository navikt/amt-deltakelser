package no.nav.amt.deltaker.navenhet

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class NavEnhetConsumer(
    private val navEnhetRepository: NavEnhetRepository,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.AMT_NAV_ENHET_TOPIC,
        consumeFunc = ::consume,
    )

    suspend fun consume(key: UUID, value: String?) {
        if (value == null) throw IllegalArgumentException("Mottok uventet tombstone for nav-enhet med id $key")

        val dto = objectMapper.readValue<NavEnhetDto>(value)
        navEnhetRepository.upsert(dto.toModel())
        log.info("Lagret nav-enhet med id $key")
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
