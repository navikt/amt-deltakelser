package no.nav.amt.deltaker.bff.navenhet

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class NavEnhetConsumer(
    private val navEnhetService: NavEnhetService,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.AMT_NAV_ENHET_TOPIC,
        consumeFunc = ::consume,
    )

    suspend fun consume(key: UUID, value: String?) {
        if (value == null) throw kotlin.IllegalArgumentException("Mottok uventet tombstone for nav-enhet med id $key")

        val dto = objectMapper.readValue<NavEnhetDto>(value)
        navEnhetService.upsert(dto.toModel())
        log.info("Lagret nav-enhet med id $key")
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
