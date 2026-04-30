package no.nav.amt.deltaker.tiltaksarrangor

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.extensions.toVurdering
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagService
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Melding
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class ArrangorMeldingConsumer(
    private val endringFraArrangorService: EndringFraArrangorService,
    private val forslagRepository: ForslagRepository,
    private val forslagService: ForslagService,
    private val deltakerRepository: DeltakerRepository,
    private val vurderingRepository: VurderingRepository,
    private val deltakerProducerService: DeltakerProducerService,
    private val isDev: Boolean = Environment.isDev(),
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.ARRANGOR_MELDING_TOPIC,
        consumeFunc = ::consume,
    )

    fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null) {
            log.warn("Mottok tombstone for melding med id: $key")
            forslagRepository.delete(key)
            return
        }

        val melding = objectMapper.readValue<Melding>(value)
        if (!(forslagRepository.kanLagres(melding.deltakerId) || melding is Vurdering)) {
            if (isDev) {
                log.error("Mottatt melding ${melding.id} på deltaker som ikke finnes, deltakerid ${melding.deltakerId}, ignorerer")
                return
            } else {
                throw RuntimeException("Mottatt melding ${melding.id} på deltaker som ikke finnes, deltakerid ${melding.deltakerId}")
            }
        }

        when (melding) {
            is EndringFraArrangor -> endringFraArrangorService.upsertEndretDeltaker(melding)
            is Forslag -> handleForslag(melding)
            is Vurdering -> handleVurdering(melding)
        }
    }

    private fun handleForslag(forslag: Forslag) = Database.transaction {
        forslagService.upsertAndProduce(forslag)
    }

    private fun handleVurdering(vurdering: Vurdering) {
        val deltaker = deltakerRepository.get(vurdering.deltakerId).getOrNull()
        if (deltaker == null) {
            log.warn("Fant ikke deltaker ${vurdering.deltakerId} for vurdering ${vurdering.id}, ignorerer")
            return
        }
        Database.transaction {
            vurderingRepository.upsert(vurdering.toVurdering())
            deltakerProducerService.produce(deltaker, publiserTilDeltakerV1 = false, publiserTilDeltakerEksternV1 = false)
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
