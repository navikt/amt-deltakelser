package no.nav.amt.deltaker.innbygger

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.deltaker.veileder.KladdService
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class NavBrukerConsumer(
    private val repository: NavBrukerRepository,
    private val navEnhetService: NavEnhetService,
    private val deltakerService: DeltakerService,
    private val kladdService: KladdService,
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
        val lagretNavBruker = repository.get(key).getOrNull()
        val navBrukerPayload = objectMapper.readValue<NavBrukerDto>(value)

        if (navBrukerPayload.innsatsgruppe == null) {
            val kladder = deltakerService.getFlereForPerson(navBrukerPayload.personident).filter {
                it.status.type == DeltakerStatus.Type.KLADD
            }
            kladder.forEach {
                kladdService.slettKladd(it.id)
                log.info("Slettet kladd med id ${it.id} fordi bruker ikke er under oppfølging")
            }
        }
        if (harEndredePersonopplysninger(lagretNavBruker, navBrukerPayload)) {
            navBrukerPayload.navEnhet?.let { navEnhetService.hentEllerOpprettNavEnhet(it.enhetId) }
            val harEndretPersonident = lagretNavBruker?.personident != navBrukerPayload.personident

            Database.transaction {
                repository.upsert(navBrukerPayload.toModel())

                deltakerService.produserDeltakereForPerson(
                    navBrukerPayload.personident,
                    publiserTilDeltakerV1 = harEndretPersonident,
                    publiserTilDeltakerEksternV1 = harEndretPersonident,
                )
            }
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    private fun harEndredePersonopplysninger(
        navBruker: NavBruker?,
        navBrukerDto: NavBrukerDto,
    ): Boolean = if (navBruker == null) {
        true
    } else {
        navBrukerDto.toModel() != navBruker
    }
}
