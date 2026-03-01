package no.nav.amt.deltaker.deltakerliste.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.deltakerliste.toModel
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class DeltakerlisteConsumer(
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val deltakerRepository: DeltakerRepository,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val arrangorService: ArrangorService,
    private val deltakerService: DeltakerService,
    private val unleashToggle: CommonUnleashToggle,
) : Consumer<UUID, String?> {
    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.DELTAKERLISTE_V2_TOPIC,
        consumeFunc = ::consume,
    )
    val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    suspend fun consume(key: UUID, value: String?) = if (value == null) {
        deltakerlisteRepository.delete(key)
    } else {
        handterDeltakerliste(objectMapper.readValue(value))
    }

    private suspend fun handterDeltakerliste(deltakerlistePayload: GjennomforingV2KafkaPayload) {
        if (!unleashToggle.skalLeseGjennomforing(deltakerlistePayload.tiltakskode.name)) {
            return
        }

        deltakerlistePayload.assertPameldingstypeIsValid()

        val arrangor = arrangorService.hentArrangor(deltakerlistePayload.arrangor.organisasjonsnummer)
        val tiltakstype = tiltakstypeRepository.get(deltakerlistePayload.tiltakskode).getOrThrow()

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

            handterDeltakere(deltakerliste, eksisterendeDeltakerliste)
        }

        deltakerlisteRepository.upsert(deltakerliste)
    }

    suspend fun handterDeltakere(deltakerlisteFromPayload: Deltakerliste, eksisterendeDeltakerliste: Deltakerliste) {
        if (deltakerlisteFromPayload.erAvlystEllerAvbrutt() && eksisterendeDeltakerliste.status != deltakerlisteFromPayload.status) {
            deltakerService.avsluttDeltakelserPaaDeltakerliste(deltakerlisteFromPayload)
        }

        if (deltakerlisteFromPayload.sluttDato != null &&
            eksisterendeDeltakerliste.sluttDato != null &&
            deltakerlisteFromPayload.sluttDato < eksisterendeDeltakerliste.sluttDato
        ) {
            deltakerService.avgrensSluttdatoerTil(deltakerlisteFromPayload)
        }
    }
}
