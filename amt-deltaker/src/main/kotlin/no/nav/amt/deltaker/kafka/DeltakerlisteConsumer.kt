package no.nav.amt.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.model.toModel
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.UUID

class DeltakerlisteConsumer(
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val deltakerRepository: DeltakerRepository,
    private val tiltakRepository: TiltakRepository,
    private val arrangorService: ArrangorService,
    private val deltakerService: DeltakerService,
    private val deltakerProducerService: DeltakerProducerService,
    private val unleashToggle: CommonUnleashToggle,
) : Consumer<UUID, String?> {
    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.DELTAKERLISTE_V2_TOPIC,
        consumeFunc = ::consume,
    )
    val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    suspend fun consume(
        key: UUID,
        value: String?,
    ) = if (value == null) {
        deltakerlisteRepository.delete(key)
    } else {
        handterDeltakerliste(objectMapper.readValue(value))
    }

    private suspend fun handterDeltakerliste(deltakerlistePayload: GjennomforingV2KafkaPayload) {
        if (!unleashToggle.skalLeseGjennomforing(deltakerlistePayload.tiltakskode.name)) {
            return
        }

        // enkelte gjennomforinger skal ikke bli lest grunnet feil
        if (GjennomforingV2KafkaPayload.gjennomforingBlacklist.contains(deltakerlistePayload.id)) {
            return
        }

        deltakerlistePayload.assertPameldingstypeIsValid()

        val arrangor = arrangorService.hentArrangor(deltakerlistePayload.arrangor.organisasjonsnummer)
        val tiltakstype = tiltakRepository.get(deltakerlistePayload.tiltakskode).getOrThrow()

        val deltakerliste = deltakerlistePayload.toModel(
            { gruppe -> gruppe.toModel(arrangor, tiltakstype) },
            { enkeltplass -> enkeltplass.toModel(arrangor, tiltakstype) },
        )

        val eksisterendeDeltakerliste = deltakerlisteRepository.get(deltakerlistePayload.id).getOrNull()

        if (eksisterendeDeltakerliste != null) {
            if (eksisterendeDeltakerliste == deltakerliste) {
                log.info("Deltakerliste med id ${deltakerliste.id} er uendret.")
                return
            }

            // deltakerliste med deltakere kan ikke endre pameldingstype eller oppstartstype
            deltakerlistePayload.assertValidChanges(
                antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(eksisterendeDeltakerliste.id),
                eksisterendePameldingstype = eksisterendeDeltakerliste.pameldingstype,
                eksisterendeOppstartstype = eksisterendeDeltakerliste.oppstart,
            )

            Database.transaction {
                deltakerlisteRepository.upsert(deltakerliste)

                handterDeltakere(
                    deltakerlisteFromPayload = deltakerliste,
                    eksisterendeDeltakerliste = eksisterendeDeltakerliste,
                )

                // hvis deltakerliste er for enkeltplass, publiser deltaker
                if (eksisterendeDeltakerliste.gjennomforingstype == GjennomforingType.Enkeltplass &&
                    eksisterendeDeltakerliste.status == GjennomforingStatusType.KLADD &&
                    deltakerliste.status != GjennomforingStatusType.KLADD
                ) {
                    deltakerProducerService.produce(
                        deltakerRepository.getEnkeltplassdeltaker(eksisterendeDeltakerliste.id).getOrThrow(),
                    )
                }
            }
        } else {
            deltakerlisteRepository.upsert(deltakerliste)
        }
    }

    private fun handterDeltakere(
        deltakerlisteFromPayload: Deltakerliste,
        eksisterendeDeltakerliste: Deltakerliste,
    ) {
        if (deltakerlisteFromPayload.erAvlystEllerAvbrutt() && eksisterendeDeltakerliste.status != deltakerlisteFromPayload.status) {
            avsluttDeltakelserPaaDeltakerliste(deltakerlisteFromPayload)
        }

        if (deltakerlisteFromPayload.sluttDato != null &&
            eksisterendeDeltakerliste.sluttDato != null &&
            deltakerlisteFromPayload.sluttDato < eksisterendeDeltakerliste.sluttDato
        ) {
            avgrensSluttdatoerTil(deltakerlisteFromPayload)
        }
    }

    internal fun avsluttDeltakelserPaaDeltakerliste(deltakerliste: Deltakerliste) {
        val deltakerePaAvbruttDeltakerliste = deltakerRepository
            .getDeltakereForAvsluttetDeltakerliste(deltakerliste.id)
            .map { it.copy(deltakerliste = deltakerliste) }

        deltakerService.avsluttDeltakere(deltakerePaAvbruttDeltakerliste)
    }

    internal fun avgrensSluttdatoerTil(deltakerliste: Deltakerliste) {
        deltakerRepository
            .getDeltakerHvorSluttdatoSkalEndres(deltakerliste.id)
            .forEach { deltaker ->
                deltakerRepository.upsert(
                    deltaker.copy(
                        sluttdato = deltakerliste.sluttDato,
                        sistEndret = LocalDateTime.now(),
                    ),
                )
                deltakerService.lagreDeltakerStatus(
                    deltakerId = deltaker.id,
                    nyDeltakerStatus = deltaker.status,
                    erDeltakerSluttdatoEndret = true,
                )
                deltakerProducerService.produce(
                    deltaker = deltakerRepository.get(deltaker.id).getOrThrow(),
                    forcedUpdate = true,
                )

                log.info("Deltaker ${deltaker.id} fikk ny sluttdato")
            }
    }
}
