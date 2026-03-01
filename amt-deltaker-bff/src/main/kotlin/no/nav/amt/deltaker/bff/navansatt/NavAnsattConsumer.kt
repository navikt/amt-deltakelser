package no.nav.amt.deltaker.bff.navansatt

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.person.dto.NavAnsattDto
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class NavAnsattConsumer(
    private val navAnsattService: NavAnsattService,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.AMT_NAV_ANSATT_TOPIC,
        consumeFunc = ::consume,
    )

    suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            navAnsattService.slettNavAnsatt(key)
            log.info("Slettet navansatt med id $key")
        } else {
            val dto = objectMapper.readValue<NavAnsattDto>(value)
            navAnsattService.oppdaterNavAnsatt(dto.toModel())
            log.info("Lagret navansatt med id $key")
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
