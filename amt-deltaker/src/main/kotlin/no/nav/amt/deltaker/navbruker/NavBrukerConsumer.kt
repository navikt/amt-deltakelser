package no.nav.amt.deltaker.navbruker

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class NavBrukerConsumer(
    private val repository: NavBrukerRepository,
    private val navEnhetService: NavEnhetService,
    private val deltakerService: DeltakerService,
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
        val lagretNavBruker = repository.get(key).getOrNull()
        val navBrukerDto = objectMapper.readValue<NavBrukerDto>(value)
        if (harEndredePersonopplysninger(lagretNavBruker, navBrukerDto)) {
            navBrukerDto.navEnhet?.let { navEnhetService.hentEllerOpprettNavEnhet(it.enhetId) }
            val harEndretPersonident = lagretNavBruker?.personident != navBrukerDto.personident

            Database.transaction {
                repository.upsert(navBrukerDto.toModel())

                deltakerService.produserDeltakereForPerson(
                    navBrukerDto.personident,
                    publiserTilDeltakerV1 = harEndretPersonident,
                    publiserTilDeltakerEksternV1 = harEndretPersonident,
                )
            }
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    private fun harEndredePersonopplysninger(navBruker: NavBruker?, navBrukerDto: NavBrukerDto): Boolean = if (navBruker == null) {
        true
    } else {
        navBrukerDto.toModel() != navBruker
    }
}
