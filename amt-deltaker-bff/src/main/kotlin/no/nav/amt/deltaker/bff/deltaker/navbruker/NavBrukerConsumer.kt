package no.nav.amt.deltaker.bff.deltaker.navbruker

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class NavBrukerConsumer(
    private val navBrukerService: NavBrukerService,
    private val pameldingService: PameldingService,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.AMT_NAV_BRUKER_TOPIC,
        consumeFunc = ::consume,
    )

    suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            log.warn("Mottok tombstone for nav-bruker: $key, skal ikke skje.")
            return
        }
        val navBruker = objectMapper.readValue<NavBrukerDto>(value).toModel()
        navBrukerService.upsert(navBruker)
        if (navBruker.innsatsgruppe == null) {
            val kladder = pameldingService.getKladder(navBruker.personident)
            kladder.forEach {
                pameldingService.slettKladd(it)
                log.info("Slettet kladd med id ${it.id} fordi bruker ikke er under oppfølging")
            }
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
