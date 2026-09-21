package no.nav.amt.deltaker.bff.innbygger

import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class NavBrukerConsumer(
    private val navBrukerService: NavBrukerService,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.AMT_NAV_BRUKER_TOPIC,
        consumeFunc = ::consume,
    )

    suspend fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null) {
            log.warn("Mottok tombstone for nav-bruker: $key, skal ikke skje.")
            return
        }
        val navBruker = objectMapper.readValue<NavBrukerDto>(value).toModel()
        navBrukerService.upsert(navBruker)
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
