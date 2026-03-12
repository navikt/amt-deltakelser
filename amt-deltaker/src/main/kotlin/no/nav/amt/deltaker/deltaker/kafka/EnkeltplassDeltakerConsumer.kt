package no.nav.amt.deltaker.deltaker.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.dto.EnkeltplassDeltakerPayload
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class EnkeltplassDeltakerConsumer(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val unleashToggle: CommonUnleashToggle,
    private val deltakerProducerService: DeltakerProducerService,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.ENKELTPLASS_DELTAKER_TOPIC,
        consumeFunc = ::consume,
    )

    suspend fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null) {
            // arena-acl skal aldri tombstone, sletting av enkeltplassdeltakere håndteres med status=feilregistrert
            throw IllegalStateException("Tombstone er ikke støttet. Key: $key")
        } else {
            log.info("Konsumerer enkeltplassdeltaker $key")
            consumeDeltaker(objectMapper.readValue(value))
            log.info("Enkeltplassdeltaker $key ferdig konsumert")
        }
    }

    suspend fun consumeDeltaker(deltakerPayload: EnkeltplassDeltakerPayload) {
        val deltakerliste = deltakerlisteRepository.get(deltakerPayload.gjennomforingId).getOrElse {
            throw NoSuchElementException("Deltakerliste ${deltakerPayload.gjennomforingId} ikke mottatt fra Mulighetsrommet ennå")
        }

        if (!unleashToggle.skalLeseArenaDataForTiltakstype(deltakerliste.tiltakstype.tiltakskode)) return

        log.info("Ingester enkeltplass deltaker med id ${deltakerPayload.id}")
        val eksisterendeDeltaker = deltakerRepository.get(deltakerPayload.id).getOrNull()
        val navBruker = navBrukerService.get(deltakerPayload.personIdent)

        // Work-around for falsk identitet i dev sånn at consumeren ikke blir stuck på noen deltakere
        if (navBruker.isFailure && Environment.isDev()) {
            log.error(
                "Klarte ikke å hente Nav-bruker med ident ${deltakerPayload.personIdent} for deltaker ${deltakerPayload.id}. Feilen var: ${navBruker.exceptionOrNull()}",
            )
            return
        }

        val deltaker = deltakerPayload.toDeltaker(
            deltakerliste = deltakerliste,
            navBruker = navBruker.getOrThrow(),
            forrigeDeltakerStatus = eksisterendeDeltaker?.status,
        )

        val erDeltakerSluttdatoEndret = eksisterendeDeltaker != null &&
            eksisterendeDeltaker.sluttdato != deltaker.sluttdato

        deltakerService
            .transactionalDeltakerUpsert(
                deltaker = deltaker,
                erDeltakerSluttdatoEndret = erDeltakerSluttdatoEndret,
                afterDeltakerUpsert = {
                    importertFraArenaRepository.upsert(deltaker.toImportertData())
                    deltakerProducerService.produce(deltaker)
                    deltaker
                },
            ).getOrThrow()
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    private fun Deltaker.toImportertData() = ImportertFraArena(
        deltakerId = id,
        importertDato = LocalDateTime.now(), // Bruker current_timestamp i db
        deltakerVedImport = this.toDeltakerVedImport(opprettet.toLocalDate()),
    )
}
